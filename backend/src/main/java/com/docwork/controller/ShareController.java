package com.docwork.controller;

import com.docwork.common.Result;
import com.docwork.entity.Document;
import com.docwork.entity.ShareLink;
import com.docwork.interceptor.UserContext;
import com.docwork.service.ShareService;
import com.docwork.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/share")
@RequiredArgsConstructor
public class ShareController {

    private final ShareService shareService;
    private final StorageService storageService;

    /** 创建分享链接 */
    @PostMapping
    public Result<ShareLink> create(@RequestBody Map<String, Object> body) {
        Long userId = UserContext.getCurrentUserId();
        Long docId = Long.parseLong(body.get("documentId").toString());
        int expireHours = Integer.parseInt(body.getOrDefault("expireHours", "24").toString());
        String password = (String) body.getOrDefault("password", "");
        int permission = Integer.parseInt(body.getOrDefault("permission", "1").toString());
        return Result.success(shareService.createShareLink(docId, userId, expireHours, password, permission));
    }

    /** 获取分享信息(公开) */
    @GetMapping("/view/{code}")
    public Result<ShareLink> getShareInfo(@PathVariable String code) {
        return Result.success(shareService.getShareByCode(code));
    }

    /** 验证提取码并访问(公开) */
    @PostMapping("/verify/{code}")
    public Result<Void> verify(@PathVariable String code, @RequestBody Map<String, String> body) {
        shareService.verifyAndAccess(code, body.get("password"));
        return Result.success();
    }

    /** 公开只读获取共享文档信息 */
    @GetMapping("/doc/{code}")
    public Result<Document> getSharedDocument(@PathVariable String code,
                                              @RequestHeader(value = "X-Share-Password", required = false) String password) {
        return Result.success(shareService.getSharedDocument(code, password));
    }

    /** 公开只读获取共享文档文本内容(markdown/txt) */
    @GetMapping("/doc/{code}/content")
    public Result<String> getSharedDocumentContent(@PathVariable String code,
                                                   @RequestHeader(value = "X-Share-Password", required = false) String password) {
        return Result.success(shareService.getSharedDocumentContent(code, password));
    }

    /** 公开只读获取共享文档下载地址(非文本文件) */
    @GetMapping("/doc/{code}/download")
    public Result<String> getSharedDownloadUrl(@PathVariable String code,
                                               @RequestHeader(value = "X-Share-Password", required = false) String password) {
        Document doc = shareService.getSharedDocument(code, password);
        return Result.success(storageService.getPresignedUrl(doc.getFileKey()));
    }

    /** 删除分享链接 */
    @DeleteMapping("/{code}")
    public Result<Void> delete(@PathVariable String code) {
        Long userId = UserContext.getCurrentUserId();
        shareService.deleteShareLink(code, userId);
        return Result.success();
    }
}
