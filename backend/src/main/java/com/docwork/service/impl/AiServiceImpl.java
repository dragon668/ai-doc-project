package com.docwork.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.docwork.common.BusinessException;
import com.docwork.entity.AiConversation;
import com.docwork.entity.AiMessage;
import com.docwork.entity.Document;
import com.docwork.mapper.AiConversationMapper;
import com.docwork.mapper.AiMessageMapper;
import com.docwork.mapper.DocumentMapper;
import com.docwork.service.AiService;
import com.docwork.common.Constants;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiServiceImpl implements AiService {

    private final AiConversationMapper conversationMapper;
    private final AiMessageMapper messageMapper;
    private final DocumentMapper documentMapper;
    private final ObjectMapper objectMapper;

    @Value("${ai-service.url}")
    private String aiServiceUrl;

    @Value("${ai-service.timeout}")
    private int aiTimeout;

    private final ExecutorService sseExecutor = Executors.newCachedThreadPool();

    @Override
    public AiConversation createConversation(Long userId, Long workspaceId, String title) {
        AiConversation conv = new AiConversation();
        conv.setUserId(userId);
        conv.setWorkspaceId(workspaceId);
        conv.setTitle(title != null ? title : "新对话");
        conversationMapper.insert(conv);
        return conv;
    }

    @Override
    public List<AiConversation> listConversations(Long userId, Long workspaceId) {
        return conversationMapper.selectList(
                new LambdaQueryWrapper<AiConversation>()
                        .eq(AiConversation::getUserId, userId)
                        .eq(AiConversation::getWorkspaceId, workspaceId)
                        .eq(AiConversation::getDeleted, 0)
                        .orderByDesc(AiConversation::getUpdateTime)
        );
    }

    @Override
    public void deleteConversation(Long conversationId, Long userId) {
        AiConversation conv = conversationMapper.selectById(conversationId);
        if (conv == null || !conv.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权操作此对话");
        }
        conversationMapper.deleteById(conversationId);
    }

    @Override
    public SseEmitter chat(Long conversationId, String question, Long userId) {
        AiConversation conv = conversationMapper.selectById(conversationId);
        if (conv == null || !conv.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权操作此对话");
        }

        SseEmitter emitter = new SseEmitter(aiTimeout * 2L);

        // 保存用户消息
        AiMessage userMsg = new AiMessage();
        userMsg.setConversationId(conversationId);
        userMsg.setRole("user");
        userMsg.setContent(question);
        messageMapper.insert(userMsg);

        // 获取历史消息(最近10轮)
        List<AiMessage> history = messageMapper.selectList(
                new LambdaQueryWrapper<AiMessage>()
                        .eq(AiMessage::getConversationId, conversationId)
                        .orderByDesc(AiMessage::getCreateTime)
                        .last("LIMIT 20")
        );

        // 获取空间中已向量化文档列表(作为知识库范围)
        List<Document> docs = documentMapper.selectList(
                new LambdaQueryWrapper<Document>()
                        .eq(Document::getWorkspaceId, conv.getWorkspaceId())
                        .eq(Document::getStatus, Constants.DOC_VECTORIZED)
                        .eq(Document::getDeleted, 0)
                        .select(Document::getId, Document::getTitle)
        );

        // 异步调用Python AI服务，通过SSE流式返回
        sseExecutor.submit(() -> {
            StringBuilder fullResponse = new StringBuilder();
            try {
                // 构造请求体
                Map<String, Object> requestBody = Map.of(
                        "question", question,
                        "history", history.stream().map(m -> Map.of("role", m.getRole(), "content", m.getContent())).toList(),
                        "doc_ids", docs.stream().map(Document::getId).toList()
                );

                String jsonBody = objectMapper.writeValueAsString(requestBody);

                // 调用Python AI服务的SSE接口
                URI uri = new URI(aiServiceUrl + "/api/rag/chat");
                HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(aiTimeout);
                conn.getOutputStream().write(jsonBody.getBytes(StandardCharsets.UTF_8));

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data: ")) {
                        String data = line.substring(6);
                        if ("[DONE]".equals(data)) {
                            emitter.send(SseEmitter.event().data("[DONE]"));
                            break;
                        }
                        fullResponse.append(data);
                        emitter.send(SseEmitter.event().data(data));
                    }
                }
                reader.close();
                conn.disconnect();

                // 保存AI回复消息
                AiMessage aiMsg = new AiMessage();
                aiMsg.setConversationId(conversationId);
                aiMsg.setRole("assistant");
                aiMsg.setContent(fullResponse.toString());
                messageMapper.insert(aiMsg);

                emitter.complete();
            } catch (Exception e) {
                log.error("AI问答异常: conversationId={}", conversationId, e);
                try {
                    emitter.send(SseEmitter.event().data("AI服务暂时不可用，请稍后重试"));
                    emitter.complete();
                } catch (Exception ex) {
                    emitter.completeWithError(ex);
                }
            }
        });

        // 连接超时和错误回调
        emitter.onTimeout(emitter::complete);
        emitter.onError(e -> log.warn("SSE连接异常: conversationId={}", conversationId));

        return emitter;
    }

    @Override
    public List<AiMessage> getMessages(Long conversationId, Long userId) {
        AiConversation conv = conversationMapper.selectById(conversationId);
        if (conv == null || !conv.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权查看此对话");
        }
        return messageMapper.selectList(
                new LambdaQueryWrapper<AiMessage>()
                        .eq(AiMessage::getConversationId, conversationId)
                        .orderByAsc(AiMessage::getCreateTime)
        );
    }
}
