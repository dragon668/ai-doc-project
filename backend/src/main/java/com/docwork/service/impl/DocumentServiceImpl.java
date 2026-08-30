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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

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

        byte[] bytes = (content == null ? "" : content).getBytes(StandardCharsets.UTF_8);
        String contentType = "text/markdown; charset=utf-8";
        if ("txt".equalsIgnoreCase(doc.getType())) {
            contentType = "text/plain; charset=utf-8";
        }

        storageService.uploadFile(doc.getFileKey(), new ByteArrayInputStream(bytes), contentType);

        doc.setFileSize((long) bytes.length);
        doc.setVersion(doc.getVersion() + 1);
        documentMapper.updateById(doc);

        DocumentVersion version = new DocumentVersion();
        version.setDocumentId(docId);
        version.setVersion(doc.getVersion());
        version.setFileKey(doc.getFileKey());
        version.setFileSize((long) bytes.length);
        version.setRemark("在线文本编辑");
        version.setOperatorId(userId);
        versionMapper.insert(version);
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
}
