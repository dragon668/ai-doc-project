package com.docwork.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.docwork.entity.Document;
import com.docwork.entity.DocumentVersion;

import java.util.List;

public interface DocumentService {

    /** 创建文档记录(上传完成后调用) */
    Document createDocument(String title, String type, long fileSize, String fileKey,
                           String md5, Long folderId, Long workspaceId, Long creatorId);

    /** 创建空白文本文档并写入初始内容 */
    Document createTextDocument(String title, String content, Long workspaceId, Long folderId, Long creatorId);

    /** 获取空间下的文档列表 */
    Page<Document> listDocuments(Long workspaceId, Long folderId, int page, int size, Long userId);

    /** 获取文档详情 */
    Document getDocument(Long docId);

    /** 获取文档文本内容（Markdown/TXT） */
    String getDocumentContent(Long docId);

    String getEditableContent(Long docId);

    /** 保存文档文本内容（Markdown/TXT） */
    void updateDocumentContent(Long docId, String content, Long userId);

    void updateEditableContent(Long docId, String content, Long userId);

    /** 删除文档(逻辑删除) */
    void deleteDocument(Long docId, Long userId);

    /** 获取文档版本列表 */
    List<DocumentVersion> getVersionHistory(Long docId);

    /** 回滚到指定版本 */
    void rollbackVersion(Long docId, int version, Long userId);

    /** 上传新版本 */
    DocumentVersion uploadNewVersion(Long docId, String fileKey, long fileSize, String remark, Long userId);

    /** 检查用户对文档的权限 */
    int checkDocPermission(Long docId, Long userId);

    /** 更新文档状态 */
    void updateDocStatus(Long docId, int status);
}
