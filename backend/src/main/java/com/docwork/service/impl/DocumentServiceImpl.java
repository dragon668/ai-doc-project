package com.docwork.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.docwork.common.BusinessException;
import com.docwork.common.Constants;
import com.docwork.entity.Document;
import com.docwork.entity.DocumentVersion;
import com.docwork.entity.WorkspaceMember;
import com.docwork.mapper.DocumentMapper;
import com.docwork.mapper.DocumentVersionMapper;
import com.docwork.mapper.WorkspaceMemberMapper;
import com.docwork.service.DocumentService;
import com.docwork.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.hslf.usermodel.HSLFShape;
import org.apache.poi.hslf.usermodel.HSLFSlide;
import org.apache.poi.hslf.usermodel.HSLFTextShape;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    private final DocumentMapper documentMapper;
    private final DocumentVersionMapper versionMapper;
    private final WorkspaceMemberMapper memberMapper;
    private final StorageService storageService;

    @Override
    public Document createDocument(String title, String type, long fileSize, String fileKey,
                                   String md5, Long folderId, Long workspaceId, Long creatorId) {
        Document doc = new Document();
        doc.setTitle(title);
        doc.setType(type);
        doc.setFileSize(fileSize);
        doc.setFileKey(fileKey);
        doc.setMd5(md5);
        doc.setFolderId(folderId != null ? folderId : 0L);
        doc.setWorkspaceId(workspaceId);
        doc.setCreatorId(creatorId);
        doc.setVersion(1);
        doc.setStatus(Constants.DOC_NORMAL);
        doc.setPermission(Constants.DOC_SPACE_VISIBLE);
        documentMapper.insert(doc);

        // 创建初始版本记录
        DocumentVersion version = new DocumentVersion();
        version.setDocumentId(doc.getId());
        version.setVersion(1);
        version.setFileKey(fileKey);
        version.setFileSize(fileSize);
        version.setRemark("初始版本");
        version.setOperatorId(creatorId);
        versionMapper.insert(version);

        return doc;
    }

    @Override
    @Transactional
    public Document createTextDocument(String title, String content, Long workspaceId, Long folderId, Long creatorId) {
        if (workspaceId == null || creatorId == null) {
            throw new BusinessException(400, "workspaceId 和 creatorId 不能为空");
        }

        String normalizedTitle = (title == null || title.trim().isEmpty()) ? "新建文档.md" : title.trim();
        String type = resolveTextDocumentType(normalizedTitle);
        String fileKey = "docs/" + UUID.randomUUID().toString().replace("-", "") + "." + type;
        String text = content == null ? "" : content;
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        String contentType = "text/markdown; charset=utf-8";
        if ("txt".equalsIgnoreCase(type)) {
            contentType = "text/plain; charset=utf-8";
        }

        storageService.uploadFile(fileKey, new ByteArrayInputStream(bytes), contentType);

        Document doc = new Document();
        doc.setTitle(normalizedTitle);
        doc.setType(type);
        doc.setFileSize((long) bytes.length);
        doc.setFileKey(fileKey);
        doc.setMd5(hashMd5(text));
        doc.setFolderId(folderId != null ? folderId : 0L);
        doc.setWorkspaceId(workspaceId);
        doc.setCreatorId(creatorId);
        doc.setVersion(1);
        doc.setStatus(Constants.DOC_NORMAL);
        doc.setPermission(Constants.DOC_SPACE_VISIBLE);
        documentMapper.insert(doc);

        DocumentVersion version = new DocumentVersion();
        version.setDocumentId(doc.getId());
        version.setVersion(1);
        version.setFileKey(fileKey);
        version.setFileSize((long) bytes.length);
        version.setRemark("新建文档");
        version.setOperatorId(creatorId);
        versionMapper.insert(version);

        return doc;
    }

    @Override
    public Page<Document> listDocuments(Long workspaceId, Long folderId, int page, int size, Long userId) {
        LambdaQueryWrapper<Document> wrapper = new LambdaQueryWrapper<Document>()
                .eq(Document::getWorkspaceId, workspaceId)
                .eq(Document::getDeleted, 0)
                .eq(folderId != null, Document::getFolderId, folderId != null ? folderId : 0L)
                .and(w -> w.eq(Document::getPermission, Constants.DOC_SPACE_VISIBLE)
                        .or().eq(Document::getCreatorId, userId))
                .orderByDesc(Document::getUpdateTime);

        return documentMapper.selectPage(new Page<>(page, size), wrapper);
    }

    @Override
    public Document getDocument(Long docId) {
        Document doc = documentMapper.selectById(docId);
        if (doc == null || Integer.valueOf(1).equals(doc.getDeleted())) {
            throw new BusinessException(404, "文档不存在");
        }
        return doc;
    }

    @Override
    public String getDocumentContent(Long docId) {
        Document doc = getDocument(docId);
        if (!isTextDocument(doc.getType())) {
            throw new BusinessException(400, "当前文档类型不支持文本内容读取");
        }
        try (InputStream inputStream = storageService.downloadFile(doc.getFileKey())) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new BusinessException(500, "读取文档内容失败: " + e.getMessage());
        }
    }

    @Override
    public String getEditableContent(Long docId) {
        Document doc = getDocument(docId);
        String type = doc.getType() == null ? "" : doc.getType().toLowerCase(Locale.ROOT);
        try (InputStream inputStream = storageService.downloadFile(doc.getFileKey())) {
            byte[] bytes = inputStream.readAllBytes();
            if (isTextDocument(type)) return new String(bytes, StandardCharsets.UTF_8);
            return extractEditableText(type, bytes);
        } catch (Exception e) {
            log.error("读取可编辑内容失败: docId={}", docId, e);
            throw new BusinessException(500, "文档解析失败，请确认文件内容未损坏");
        }
    }

    @Override
    @Transactional
    public void updateDocumentContent(Long docId, String content, Long userId) {
        Document doc = getDocument(docId);
        int role = checkDocPermission(docId, userId);
        if (role > Constants.ROLE_EDITOR) {
            throw new BusinessException(403, "无权编辑此文档");
        }
        if (!isTextDocument(doc.getType())) {
            throw new BusinessException(400, "当前文档类型不支持在线编辑");
        }

        String safeType = doc.getType().toLowerCase(Locale.ROOT);
        byte[] bytes = (content == null ? "" : content).getBytes(StandardCharsets.UTF_8);
        String contentType = "text/markdown; charset=utf-8";
        if ("txt".equals(safeType)) {
            contentType = "text/plain; charset=utf-8";
        }

        int newVersion = doc.getVersion() == null ? 1 : doc.getVersion() + 1;
        // 每次在线编辑生成新的不可变文件对象，保证历史版本内容可追溯、可回滚
        String newFileKey = "docs/" + doc.getId() + "/v" + newVersion + "." + safeType;
        storageService.uploadFile(newFileKey, new ByteArrayInputStream(bytes), contentType);

        doc.setFileKey(newFileKey);
        doc.setFileSize((long) bytes.length);
        doc.setMd5(hashMd5(content == null ? "" : content));
        doc.setVersion(newVersion);
        documentMapper.updateById(doc);

        DocumentVersion version = new DocumentVersion();
        version.setDocumentId(docId);
        version.setVersion(newVersion);
        version.setFileKey(newFileKey);
        version.setFileSize((long) bytes.length);
        version.setRemark("在线文本编辑");
        version.setOperatorId(userId);
        versionMapper.insert(version);
    }

    @Override
    @Transactional
    public void updateEditableContent(Long docId, String content, Long userId) {
        Document doc = getDocument(docId);
        int role = checkDocPermission(docId, userId);
        if (role > Constants.ROLE_EDITOR) {
            throw new BusinessException(403, "无权编辑此文档");
        }
        String text = content == null ? "" : content;
        int newVersion = doc.getVersion() == null ? 1 : doc.getVersion() + 1;
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        String newFileKey = "docs/" + doc.getId() + "/v" + newVersion + ".md";
        storageService.uploadFile(newFileKey, new ByteArrayInputStream(bytes), "text/markdown; charset=utf-8");
        doc.setFileKey(newFileKey);
        doc.setFileSize((long) bytes.length);
        doc.setMd5(hashMd5(text));
        doc.setType("md");
        doc.setVersion(newVersion);
        documentMapper.updateById(doc);

        DocumentVersion version = new DocumentVersion();
        version.setDocumentId(docId);
        version.setVersion(newVersion);
        version.setFileKey(newFileKey);
        version.setFileSize((long) bytes.length);
        version.setRemark("转换为在线编辑稿");
        version.setOperatorId(userId);
        versionMapper.insert(version);
    }

    private String extractEditableText(String type, byte[] bytes) throws Exception {
        if ("pdf".equals(type)) {
            try (var pdf = Loader.loadPDF(bytes)) {
                return new PDFTextStripper().getText(pdf);
            }
        }
        if ("docx".equals(type)) {
            try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
                return document.getParagraphs().stream().map(XWPFParagraph::getText)
                        .filter(text -> !text.isBlank()).reduce((left, right) -> left + "\n\n" + right).orElse("");
            }
        }
        if ("doc".equals(type)) {
            try (HWPFDocument document = new HWPFDocument(new ByteArrayInputStream(bytes))) {
                return document.getRange().text();
            }
        }
        if ("xlsx".equals(type) || "xls".equals(type)) {
            StringBuilder result = new StringBuilder();
            try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
                for (Sheet sheet : workbook) {
                    result.append("## ").append(sheet.getSheetName()).append("\n\n");
                    for (Row row : sheet) {
                        List<String> cells = new java.util.ArrayList<>();
                        for (Cell cell : row) cells.add(cell.toString().replace("|", "\\|"));
                        if (!cells.isEmpty()) result.append("| ").append(String.join(" | ", cells)).append(" |\n");
                    }
                    result.append("\n");
                }
            }
            return result.toString();
        }
        if ("pptx".equals(type)) {
            StringBuilder result = new StringBuilder();
            try (XMLSlideShow slideshow = new XMLSlideShow(new ByteArrayInputStream(bytes))) {
                int slideNumber = 1;
                for (XSLFSlide slide : slideshow.getSlides()) {
                    result.append("## 幻灯片 ").append(slideNumber++).append("\n\n");
                    for (XSLFShape shape : slide.getShapes()) {
                        if (shape instanceof XSLFTextShape textShape && !textShape.getText().isBlank()) {
                            result.append(textShape.getText()).append("\n\n");
                        }
                    }
                }
            }
            return result.toString();
        }
        if ("ppt".equals(type)) {
            StringBuilder result = new StringBuilder();
            try (HSLFSlideShow slideshow = new HSLFSlideShow(new ByteArrayInputStream(bytes))) {
                int slideNumber = 1;
                for (HSLFSlide slide : slideshow.getSlides()) {
                    result.append("## 幻灯片 ").append(slideNumber++).append("\n\n");
                    for (HSLFShape shape : slide.getShapes()) {
                        if (shape instanceof HSLFTextShape textShape && !textShape.getText().isBlank()) {
                            result.append(textShape.getText()).append("\n\n");
                        }
                    }
                }
            }
            return result.toString();
        }
        throw new BusinessException(400, "当前格式暂不支持在线编辑");
    }

    @Override
    public void deleteDocument(Long docId, Long userId) {
        Document doc = getDocument(docId);
        // 检查权限：文档创建者或空间管理员以上可删除
        int role = checkDocPermission(docId, userId);
        if (role > Constants.ROLE_ADMIN) {
            throw new BusinessException(403, "无权删除此文档");
        }
        documentMapper.deleteById(docId);
    }

    @Override
    public List<DocumentVersion> getVersionHistory(Long docId) {
        return versionMapper.selectList(
                new LambdaQueryWrapper<DocumentVersion>()
                        .eq(DocumentVersion::getDocumentId, docId)
                        .orderByDesc(DocumentVersion::getVersion)
        );
    }

    @Override
    @Transactional
    public void rollbackVersion(Long docId, int version, Long userId) {
        Document doc = getDocument(docId);
        DocumentVersion targetVersion = versionMapper.selectOne(
                new LambdaQueryWrapper<DocumentVersion>()
                        .eq(DocumentVersion::getDocumentId, docId)
                        .eq(DocumentVersion::getVersion, version)
        );
        if (targetVersion == null) {
            throw new BusinessException("指定版本不存在");
        }

        // 更新文档的fileKey指向目标版本
        doc.setFileKey(targetVersion.getFileKey());
        doc.setFileSize(targetVersion.getFileSize());
        doc.setVersion(doc.getVersion() + 1);
        documentMapper.updateById(doc);

        // 记录新版本
        DocumentVersion newVersion = new DocumentVersion();
        newVersion.setDocumentId(docId);
        newVersion.setVersion(doc.getVersion());
        newVersion.setFileKey(targetVersion.getFileKey());
        newVersion.setFileSize(targetVersion.getFileSize());
        newVersion.setRemark("从版本" + version + "回滚");
        newVersion.setOperatorId(userId);
        versionMapper.insert(newVersion);
    }

    @Override
    @Transactional
    public DocumentVersion uploadNewVersion(Long docId, String fileKey, long fileSize, String remark, Long userId) {
        Document doc = getDocument(docId);
        int newVersion = doc.getVersion() + 1;

        doc.setFileKey(fileKey);
        doc.setFileSize(fileSize);
        doc.setVersion(newVersion);
        documentMapper.updateById(doc);

        DocumentVersion version = new DocumentVersion();
        version.setDocumentId(docId);
        version.setVersion(newVersion);
        version.setFileKey(fileKey);
        version.setFileSize(fileSize);
        version.setRemark(remark != null ? remark : "版本更新");
        version.setOperatorId(userId);
        versionMapper.insert(version);

        return version;
    }

    @Override
    public int checkDocPermission(Long docId, Long userId) {
        Document doc = getDocument(docId);
        // 文档创建者拥有完全权限
        if (doc.getCreatorId() != null && doc.getCreatorId().equals(userId)) {
            return Constants.ROLE_OWNER;
        }
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }
        // 查询空间角色
        WorkspaceMember member = memberMapper.selectOne(
                new LambdaQueryWrapper<WorkspaceMember>()
                        .eq(WorkspaceMember::getWorkspaceId, doc.getWorkspaceId())
                        .eq(WorkspaceMember::getUserId, userId)
        );
        if (member == null) {
            // 非空间成员，只能访问空间可见的公开文档
            if (Integer.valueOf(Constants.DOC_SPACE_VISIBLE).equals(doc.getPermission())) {
                return Constants.ROLE_VIEWER;
            }
            throw new BusinessException(403, "无权访问此文档");
        }
        return member.getRole();
    }

    @Override
    public void updateDocStatus(Long docId, int status) {
        Document doc = new Document();
        doc.setId(docId);
        doc.setStatus(status);
        documentMapper.updateById(doc);
    }

    private boolean isTextDocument(String type) {
        if (type == null) {
            return false;
        }
        String normalized = type.toLowerCase();
        return "md".equals(normalized) || "markdown".equals(normalized) || "txt".equals(normalized);
    }

    private String resolveTextDocumentType(String title) {
        if (title == null) {
            return "md";
        }
        String lower = title.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".txt")) {
            return "txt";
        }
        return lower.endsWith(".md") || lower.endsWith(".markdown") ? "md" : "md";
    }

    private String hashMd5(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception e) {
            return "";
        }
    }
}
