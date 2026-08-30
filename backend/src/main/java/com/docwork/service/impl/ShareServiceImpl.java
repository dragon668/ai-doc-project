package com.docwork.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.docwork.common.BusinessException;
import com.docwork.entity.Document;
import com.docwork.entity.ShareLink;
import com.docwork.mapper.DocumentMapper;
import com.docwork.mapper.ShareLinkMapper;
import com.docwork.service.ShareService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShareServiceImpl implements ShareService {

    private final ShareLinkMapper shareLinkMapper;
    private final DocumentMapper documentMapper;

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
}
