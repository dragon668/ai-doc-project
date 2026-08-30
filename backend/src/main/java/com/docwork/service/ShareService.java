package com.docwork.service;

import com.docwork.entity.ShareLink;

public interface ShareService {
    ShareLink createShareLink(Long documentId, Long creatorId, int expireHours, String password, int permission);
    ShareLink getShareByCode(String code);
    void verifyAndAccess(String code, String password);
    void deleteShareLink(String code, Long userId);
}
