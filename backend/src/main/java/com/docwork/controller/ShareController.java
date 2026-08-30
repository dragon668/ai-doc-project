package com.docwork.controller;

import com.docwork.common.Result;
import com.docwork.entity.ShareLink;
import com.docwork.interceptor.UserContext;
import com.docwork.service.ShareService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/share")
@RequiredArgsConstructor
public class ShareController {

    private final ShareService shareService;

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

    /** 删除分享链接 */
    @DeleteMapping("/{code}")
    public Result<Void> delete(@PathVariable String code) {
        Long userId = UserContext.getCurrentUserId();
        shareService.deleteShareLink(code, userId);
        return Result.success();
    }
}
