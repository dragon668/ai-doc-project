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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final DocumentMapper documentMapper;
    private final ShareLinkMapper shareLinkMapper;
    private final AiConversationMapper conversationMapper;
    private final UserMapper userMapper;
        private final com.docwork.mapper.DocumentVersionMapper versionMapper;
        private final com.docwork.mapper.OperationLogMapper operationLogMapper;

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

        LocalDateTime activityStart = LocalDate.now().minusDays(364).atStartOfDay();
        long editCount = versionMapper.selectCount(new LambdaQueryWrapper<DocumentVersion>()
                .eq(DocumentVersion::getOperatorId, userId)
                .ge(DocumentVersion::getCreateTime, activityStart));
        long loggedContributions = operationLogMapper.selectCount(new LambdaQueryWrapper<OperationLog>()
                .eq(OperationLog::getUserId, userId)
                .ge(OperationLog::getCreateTime, activityStart));
        List<OperationLog> logs = operationLogMapper.selectList(new LambdaQueryWrapper<OperationLog>()
                .eq(OperationLog::getUserId, userId)
                .ge(OperationLog::getCreateTime, activityStart));
        List<Integer> activity = new ArrayList<>();
        for (int day = 364; day >= 0; day--) {
            LocalDate date = LocalDate.now().minusDays(day);
            int count = (int) logs.stream().filter(log -> log.getCreateTime() != null && log.getCreateTime().toLocalDate().equals(date)).count();
            activity.add(count);
        }
        long contributionCount = editCount + loggedContributions;
        long activeDays = activity.stream().filter(count -> count > 0).count();

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
        vo.setEditCount(editCount);
        vo.setContributionCount(contributionCount);
        vo.setActiveDays(activeDays);
        vo.setActivity(activity);

        return vo;
    }
}
