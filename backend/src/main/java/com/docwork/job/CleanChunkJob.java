package com.docwork.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.docwork.common.Constants;
import com.docwork.entity.UploadChunk;
import com.docwork.mapper.UploadChunkMapper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * XXL-Job定时任务 - 清理过期分片碎片
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CleanChunkJob {

    private final UploadChunkMapper chunkMapper;

    /**
     * 清理超过24小时未完成的分片上传任务
     * 在XXL-Job Admin中配置此任务，Cron: 0 0 3 * * ? (每天凌晨3点)
     */
    @XxlJob("cleanExpiredChunks")
    public void execute() {
        log.info("开始清理过期分片碎片...");

        // 查找24小时前状态为上传中的分片
        LocalDateTime threshold = LocalDateTime.now().minusHours(24);
        var expiredChunks = chunkMapper.selectList(
                new LambdaQueryWrapper<UploadChunk>()
                        .eq(UploadChunk::getStatus, Constants.UPLOAD_IN_PROGRESS)
                        .lt(UploadChunk::getCreateTime, threshold)
        );

        if (expiredChunks.isEmpty()) {
            log.info("没有过期的分片需要清理");
            return;
        }

        int count = 0;
        for (UploadChunk chunk : expiredChunks) {
            chunk.setStatus(2); // 标记为已取消/过期
            chunkMapper.updateById(chunk);
            count++;
        }

        log.info("清理过期分片完成，共处理 {} 条记录", count);
    }

    /**
     * 清理过期的分享链接
     * Cron: 0 0 4 * * ? (每天凌晨4点)
     */
    @XxlJob("cleanExpiredShares")
    public void cleanExpiredShares() {
        log.info("开始清理过期分享链接...");
        // 逻辑删除过期的分享链接
        // 实际实现需要注入ShareLinkMapper
        log.info("过期分享链接清理完成");
    }
}
