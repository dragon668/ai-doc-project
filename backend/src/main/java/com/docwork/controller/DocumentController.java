package com.docwork.controller;

import com.docwork.common.BusinessException;
import com.docwork.common.Constants;
import com.docwork.common.Result;
import com.docwork.dto.ChunkUploadDTO;
import com.docwork.dto.ChunkUploadResultVO;
import com.docwork.dto.DocParseMessage;
import com.docwork.entity.Document;
import com.docwork.entity.DocumentVersion;
import com.docwork.interceptor.UserContext;
import com.docwork.service.DocumentService;
import com.docwork.service.StorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/doc")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;
    private final StorageService storageService;
    private final RocketMQTemplate rocketMQTemplate;
    private final ObjectMapper objectMapper;

    /** 初始化分片上传 */
    @PostMapping("/upload/init")
    public Result<ChunkUploadResultVO> initUpload(
            @RequestParam String fileName,
            @RequestParam String md5,
            @RequestParam int totalChunks,
            @RequestParam long totalSize,
            @RequestParam Long workspaceId,
            @RequestParam(required = false) Long folderId) {

        Long userId = UserContext.getCurrentUserId();
        String uploadId = UUID.randomUUID().toString().replace("-", "");

        ChunkUploadResultVO result = storageService.initChunkUpload(uploadId, fileName, md5, totalChunks, totalSize, userId);

        // 秒传成功时直接创建文档记录
        if (result.isQuickUpload()) {
            String type = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
            documentService.createDocument(fileName, type, totalSize, result.getFileKey(), md5, folderId, workspaceId, userId);
        }

        return Result.success(result);
    }

    /** 上传分片 */
    @PostMapping("/upload/chunk")
    public Result<Void> uploadChunk(@Valid ChunkUploadDTO dto, @RequestParam("file") MultipartFile file) {
        storageService.uploadChunk(dto, file);
        return Result.success();
    }

    /** 合并分片 */
    @PostMapping("/upload/merge")
    public Result<Document> mergeChunks(
            @RequestParam String uploadId,
            @RequestParam String fileName,
            @RequestParam String md5,
            @RequestParam Long workspaceId,
            @RequestParam(required = false) Long folderId) {

        Long userId = UserContext.getCurrentUserId();
        String fileKey = storageService.mergeChunks(uploadId, fileName, md5, userId);

        String type = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
        // 从MinIO获取合并后的文件大小
        long fileSize = storageService.getFileSize(fileKey);

        Document doc = documentService.createDocument(fileName, type, fileSize, fileKey, md5, folderId, workspaceId, userId);

        // 发送MQ异步消息：触发文档解析向量化
        try {
            DocParseMessage parseMsg = new DocParseMessage();
            parseMsg.setDocumentId(doc.getId());
            parseMsg.setWorkspaceId(workspaceId);
            parseMsg.setFileKey(fileKey);
            parseMsg.setDocType(type);
            parseMsg.setAction("VECTORIZE");
            String msgJson = objectMapper.writeValueAsString(parseMsg);
            rocketMQTemplate.convertAndSend(
                    Constants.MQ_TOPIC_DOC_PARSE + ":" + Constants.MQ_TAG_VECTORIZE, msgJson);
            documentService.updateDocStatus(doc.getId(), Constants.DOC_PARSING);
            log.info("已发送文档向量化MQ消息: docId={}", doc.getId());
        } catch (Exception e) {
            log.error("发送MQ消息失败，文档将不会自动向量化: docId={}", doc.getId(), e);
        }

        return Result.success(doc);
    }

    /** 获取文档列表 */
    @GetMapping("/list")
    public Result<?> listDocuments(
            @RequestParam Long workspaceId,
            @RequestParam(required = false) Long folderId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        Long userId = UserContext.getCurrentUserId();
        return Result.success(documentService.listDocuments(workspaceId, folderId, page, size, userId));
    }

    /** 创建空白文本文档 */
    @PostMapping("/text")
    public Result<Document> createTextDocument(@RequestBody java.util.Map<String, Object> body) {
        Long userId = UserContext.getCurrentUserId();
        Long workspaceId = body == null || body.get("workspaceId") == null ? null : Long.valueOf(String.valueOf(body.get("workspaceId")));
        Long folderId = body == null || body.get("folderId") == null ? null : Long.valueOf(String.valueOf(body.get("folderId")));
        String title = body == null || body.get("title") == null ? "新建文档.md" : String.valueOf(body.get("title"));
        String content = body == null || body.get("content") == null ? "# 新建文档\n\n" : String.valueOf(body.get("content"));

        if (workspaceId == null) {
            throw new BusinessException(400, "workspaceId 不能为空");
        }

        return Result.success(documentService.createTextDocument(title, content, workspaceId, folderId, userId));
    }

    /** 获取文档详情 */
    @GetMapping("/{docId}")
    public Result<Document> getDocument(@PathVariable Long docId) {
        return Result.success(documentService.getDocument(docId));
    }

    /** 获取文本文档内容 */
    @GetMapping("/{docId}/content")
    public Result<String> getDocumentContent(@PathVariable Long docId) {
        return Result.success(documentService.getDocumentContent(docId));
    }

    /** 保存文本文档内容 */
    @PostMapping("/{docId}/content")
    public Result<Void> updateDocumentContent(@PathVariable Long docId, @RequestBody java.util.Map<String, String> body) {
        Long userId = UserContext.getCurrentUserId();
        documentService.updateDocumentContent(docId, body.getOrDefault("content", ""), userId);
        return Result.success();
    }

    /** 删除文档 */
    @DeleteMapping("/{docId}")
    public Result<Void> deleteDocument(@PathVariable Long docId) {
        Long userId = UserContext.getCurrentUserId();
        documentService.deleteDocument(docId, userId);
        return Result.success();
    }

    /** 获取版本历史 */
    @GetMapping("/{docId}/versions")
    public Result<List<DocumentVersion>> getVersionHistory(@PathVariable Long docId) {
        return Result.success(documentService.getVersionHistory(docId));
    }

    /** 回滚版本 */
    @PostMapping("/{docId}/rollback/{version}")
    public Result<Void> rollbackVersion(@PathVariable Long docId, @PathVariable int version) {
        Long userId = UserContext.getCurrentUserId();
        documentService.rollbackVersion(docId, version, userId);
        return Result.success();
    }

    /** 下载文档 */
    @GetMapping("/{docId}/download")
    public Result<String> getDownloadUrl(@PathVariable Long docId) {
        Document doc = documentService.getDocument(docId);
        String url = storageService.getPresignedUrl(doc.getFileKey());
        return Result.success(url);
    }
}
