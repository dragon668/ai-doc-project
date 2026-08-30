package com.docwork.mq;

import com.docwork.common.Constants;
import com.docwork.dto.DocParseMessage;
import com.docwork.service.DocumentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * RocketMQ消费者 - 处理文档解析向量化异步任务
 * 实现消息幂等：通过Redis分布式锁防止重复向量化
 */
@Slf4j
@Service
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = Constants.MQ_TOPIC_DOC_PARSE,
        selectorExpression = Constants.MQ_TAG_VECTORIZE,
        consumerGroup = "doc-parse-consumer-group"
)
public class DocParseConsumer implements RocketMQListener<String> {

    private final DocumentService documentService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AiDocumentClient aiDocumentClient;

    @Override
    public void onMessage(String message) {
        try {
            DocParseMessage parseMsg = objectMapper.readValue(message, DocParseMessage.class);
            Long docId = parseMsg.getDocumentId();

            // 幂等处理：使用Redis SETNX防止重复消费
            String lockKey = "lock:doc:parse:" + docId;
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", 300, TimeUnit.SECONDS);

            if (Boolean.FALSE.equals(acquired)) {
                log.info("文档解析任务已在处理中，跳过重复消息: docId={}", docId);
                return;
            }

            try {
                log.info("开始处理文档解析任务: docId={}, type={}", docId, parseMsg.getDocType());
                documentService.updateDocStatus(docId, Constants.DOC_PARSING);

                aiDocumentClient.vectorizeDocument(
                        docId,
                        parseMsg.getWorkspaceId(),
                        parseMsg.getFileKey(),
                        parseMsg.getDocType(),
                        documentService.getDocument(docId).getTitle()
                );

                documentService.updateDocStatus(docId, Constants.DOC_VECTORIZED);
                log.info("文档解析向量化完成: docId={}", docId);

            } catch (Exception e) {
                documentService.updateDocStatus(docId, Constants.DOC_PARSE_FAILED);
                log.error("文档解析向量化失败: docId={}", docId, e);
                throw e; // 重试
            }

        } catch (Exception e) {
            log.error("消息消费异常", e);
        }
    }
}
