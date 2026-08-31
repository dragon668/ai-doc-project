package com.docwork.service;

import com.docwork.entity.Document;
import com.docwork.entity.ShareLink;

public interface ShareService {
    ShareLink createShareLink(Long documentId, Long creatorId, int expireHours, String password, int permission);
    ShareLink getShareByCode(String code);
    void verifyAndAccess(String code, String password);
    void deleteShareLink(String code, Long userId);

    /**
     * 通过分享码获取共享文档(公开只读访问)，校验链接有效性与提取码
     */
    Document getSharedDocument(String code, String password);

    /**
     * 通过分享码获取共享文档文本内容(仅 markdown/txt 可读)
     */
    String getSharedDocumentContent(String code, String password);
}
