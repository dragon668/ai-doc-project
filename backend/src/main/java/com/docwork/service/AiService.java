package com.docwork.service;

import com.docwork.entity.AiConversation;
import com.docwork.entity.AiMessage;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

public interface AiService {
    AiConversation createConversation(Long userId, Long workspaceId, String title);
    List<AiConversation> listConversations(Long userId, Long workspaceId);
    void deleteConversation(Long conversationId, Long userId);

    /** SSE流式问答 */
    SseEmitter chat(Long conversationId, String question, Long userId);

    /** 获取对话历史消息 */
    List<AiMessage> getMessages(Long conversationId, Long userId);
}
