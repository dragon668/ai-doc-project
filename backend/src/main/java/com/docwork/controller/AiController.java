package com.docwork.controller;

import com.docwork.common.Result;
import com.docwork.dto.AiChatDTO;
import com.docwork.entity.AiConversation;
import com.docwork.entity.AiMessage;
import com.docwork.interceptor.UserContext;
import com.docwork.service.AiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;

    /** 创建对话 */
    @PostMapping("/conversation")
    public Result<AiConversation> createConversation(@RequestBody Map<String, Object> body) {
        Long userId = UserContext.getCurrentUserId();
        Long workspaceId = Long.parseLong(body.get("workspaceId").toString());
        String title = (String) body.getOrDefault("title", "新对话");
        return Result.success(aiService.createConversation(userId, workspaceId, title));
    }

    /** 获取对话列表 */
    @GetMapping("/conversation/list")
    public Result<List<AiConversation>> listConversations(@RequestParam Long workspaceId) {
        Long userId = UserContext.getCurrentUserId();
        return Result.success(aiService.listConversations(userId, workspaceId));
    }

    /** 删除对话 */
    @DeleteMapping("/conversation/{conversationId}")
    public Result<Void> deleteConversation(@PathVariable Long conversationId) {
        Long userId = UserContext.getCurrentUserId();
        aiService.deleteConversation(conversationId, userId);
        return Result.success();
    }

    /** SSE流式AI问答 */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@Valid @RequestBody AiChatDTO dto) {
        Long userId = UserContext.getCurrentUserId();
        return aiService.chat(dto.getConversationId(), dto.getQuestion(), userId);
    }

    /** 获取对话消息历史 */
    @GetMapping("/conversation/{conversationId}/messages")
    public Result<List<AiMessage>> getMessages(@PathVariable Long conversationId) {
        Long userId = UserContext.getCurrentUserId();
        return Result.success(aiService.getMessages(conversationId, userId));
    }
}
