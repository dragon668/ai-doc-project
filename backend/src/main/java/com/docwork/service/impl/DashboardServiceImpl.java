package com.docwork.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.docwork.common.BusinessException;
import com.docwork.entity.*;
import com.docwork.mapper.*;
import com.docwork.service.DashboardService;
import com.docwork.dto.DashboardVO;
import com.docwork.common.Constants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final DocumentMapper documentMapper;
    private final ShareLinkMapper shareLinkMapper;
    private final AiConversationMapper conversationMapper;
    private final UserMapper userMapper;

    @Override
    public DashboardVO getDashboardData(Long userId) {
        User user = userMapper.selectById(userId);
        DashboardVO vo = new DashboardVO();

        long totalDocs = documentMapper.selectCount(
                new LambdaQueryWrapper<Document>()
                        .eq(Document::getCreatorId, userId)
                        .eq(Document::getDeleted, 0)
        );
        long vectorizedDocs = documentMapper.selectCount(
                new LambdaQueryWrapper<Document>()
                        .eq(Document::getCreatorId, userId)
                        .eq(Document::getStatus, Constants.DOC_VECTORIZED)
                        .eq(Document::getDeleted, 0)
        );
        long parsingDocs = documentMapper.selectCount(
                new LambdaQueryWrapper<Document>()
                        .eq(Document::getCreatorId, userId)
                        .in(Document::getStatus, Constants.DOC_PARSING, Constants.DOC_UPLOADING)
                        .eq(Document::getDeleted, 0)
        );

        long totalSize = documentMapper.selectList(
                new LambdaQueryWrapper<Document>()
                        .select(Document::getFileSize)
                        .eq(Document::getCreatorId, userId)
                        .eq(Document::getDeleted, 0)
        ).stream().mapToLong(d -> d.getFileSize() != null ? d.getFileSize() : 0).sum();

        long sharedLinks = shareLinkMapper.selectCount(
                new LambdaQueryWrapper<ShareLink>()
                        .eq(ShareLink::getCreatorId, userId)
                        .eq(ShareLink::getDeleted, 0)
                        .gt(ShareLink::getExpireTime, java.time.LocalDateTime.now())
        );

        long totalConversations = conversationMapper.selectCount(
                new LambdaQueryWrapper<AiConversation>()
                        .eq(AiConversation::getUserId, userId)
                        .eq(AiConversation::getDeleted, 0)
        );

        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }

        vo.setTotalDocs(totalDocs);
        vo.setTotalSize(totalSize);
        vo.setUsedStorage(user.getUsedStorage());
        vo.setTotalStorage(user.getTotalStorage());
        vo.setVectorizedDocs(vectorizedDocs);
        vo.setParsingDocs(parsingDocs);
        vo.setSharedLinks(sharedLinks);
        vo.setTotalConversations(totalConversations);

        return vo;
    }
}
