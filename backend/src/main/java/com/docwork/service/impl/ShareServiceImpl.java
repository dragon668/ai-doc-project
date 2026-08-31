package com.docwork.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.docwork.common.BusinessException;
import com.docwork.entity.Document;
import com.docwork.entity.ShareLink;
import com.docwork.mapper.DocumentMapper;
import com.docwork.mapper.ShareLinkMapper;
import com.docwork.service.ShareService;
import com.docwork.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShareServiceImpl implements ShareService {

    private final ShareLinkMapper shareLinkMapper;
    private final DocumentMapper documentMapper;
    private final StorageService storageService;

    @Override
    public ShareLink createShareLink(Long documentId, Long creatorId, int expireHours, String password, int permission) {
        Document doc = documentMapper.selectById(documentId);
        if (doc == null) {
            throw new BusinessException(404, "文档不存在");
        }
        if (!doc.getCreatorId().equals(creatorId)) {
            throw new BusinessException(403, "只能分享自己的文档");
        }

        ShareLink link = new ShareLink();
        link.setCode(UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        link.setDocumentId(documentId);
        link.setCreatorId(creatorId);
        link.setPermission(permission == 2 ? 2 : 1);
        link.setExpireTime(LocalDateTime.now().plusHours(expireHours));
        link.setViewCount(0);
        link.setMaxViews(-1);
        link.setPassword(password != null ? password : "");
        shareLinkMapper.insert(link);

        return link;
    }

    @Override
    public ShareLink getShareByCode(String code) {
        ShareLink link = shareLinkMapper.selectOne(
                new LambdaQueryWrapper<ShareLink>()
                        .eq(ShareLink::getCode, code)
                        .eq(ShareLink::getDeleted, 0)
        );
        if (link == null) {
            throw new BusinessException(404, "分享链接不存在");
        }
        if (link.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException(400, "分享链接已过期");
        }
        if (link.getMaxViews() > 0 && link.getViewCount() >= link.getMaxViews()) {
            throw new BusinessException(400, "分享链接已达到最大查看次数");
        }
        return link;
    }

    @Override
    public void verifyAndAccess(String code, String password) {
        ShareLink link = getShareByCode(code);
        if (link.getPassword() != null && !link.getPassword().isEmpty()) {
            if (password == null || !link.getPassword().equals(password)) {
                throw new BusinessException(403, "提取码错误");
            }
        }
        // 增加查看次数
        link.setViewCount(link.getViewCount() + 1);
        shareLinkMapper.updateById(link);
    }

    @Override
    public void deleteShareLink(String code, Long userId) {
        ShareLink link = shareLinkMapper.selectOne(
                new LambdaQueryWrapper<ShareLink>()
                        .eq(ShareLink::getCode, code)
        );
        if (link == null) {
            throw new BusinessException(404, "分享链接不存在");
        }
        if (!link.getCreatorId().equals(userId)) {
            throw new BusinessException(403, "只能删除自己创建的分享链接");
        }
        shareLinkMapper.deleteById(link.getId());
    }

    @Override
    public Document getSharedDocument(String code, String password) {
        ShareLink link = resolveAndVerify(code, password);
        Document doc = documentMapper.selectById(link.getDocumentId());
        if (doc == null || Integer.valueOf(1).equals(doc.getDeleted())) {
            throw new BusinessException(404, "共享文档不存在");
        }
        return doc;
    }

    @Override
    public String getSharedDocumentContent(String code, String password) {
        Document doc = getSharedDocument(code, password);
        if (doc.getType() == null) {
            throw new BusinessException(400, "当前文档类型不支持文本内容读取");
        }
        String type = doc.getType().toLowerCase(Locale.ROOT);
        if (!"md".equals(type) && !"markdown".equals(type) && !"txt".equals(type)) {
            throw new BusinessException(400, "当前文档类型不支持在线文本预览");
        }
        try (InputStream inputStream = storageService.downloadFile(doc.getFileKey())) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "读取共享文档内容失败: " + e.getMessage());
        }
    }

    private ShareLink resolveAndVerify(String code, String password) {
        ShareLink link = getShareByCode(code);
        if (link.getPassword() != null && !link.getPassword().isEmpty()) {
            if (password == null || !link.getPassword().equals(password)) {
                throw new BusinessException(403, "提取码错误");
            }
        }
        // 只读访问，累计查看次数
        link.setViewCount(link.getViewCount() + 1);
        shareLinkMapper.updateById(link);
        return link;
    }
}
