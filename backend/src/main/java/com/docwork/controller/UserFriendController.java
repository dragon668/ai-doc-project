package com.docwork.controller;

import com.docwork.common.Result;
import com.docwork.entity.User;
import com.docwork.entity.UserFriend;
import com.docwork.entity.ChatMessage;
import com.docwork.common.BusinessException;
import com.docwork.interceptor.UserContext;
import com.docwork.mapper.UserMapper;
import com.docwork.mapper.UserFriendMapper;
import com.docwork.mapper.ChatMessageMapper;
import com.docwork.common.SecretCryptoService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

@RestController
@RequestMapping("/friend")
@RequiredArgsConstructor
public class UserFriendController {

    private final UserFriendMapper userFriendMapper;
    private final UserMapper userMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final SecretCryptoService secretCryptoService;

    @GetMapping("/list")
    public Result<List<Map<String, Object>>> listFriends() {
        Long userId = UserContext.getCurrentUserId();
        List<UserFriend> relations = userFriendMapper.selectList(
                new LambdaQueryWrapper<UserFriend>()
                        .eq(UserFriend::getUserId, userId)
                        .eq(UserFriend::getStatus, 1)
        );
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (UserFriend relation : relations) {
            User friend = userMapper.selectById(relation.getFriendId());
            if (friend == null) continue;
            Map<String, Object> item = new HashMap<>();
            item.put("id", friend.getId());
            item.put("username", friend.getUsername());
            item.put("nickname", friend.getNickname());
            item.put("email", friend.getEmail());
            result.add(item);
        }
        return Result.success(result);
    }

    @PostMapping("/add")
    public Result<Void> addFriend(@RequestBody Map<String, Object> body) {
        Long userId = UserContext.getCurrentUserId();
        Long friendId = Long.parseLong(body.get("friendId").toString());
        if (friendId.equals(userId)) {
            throw new RuntimeException("不能添加自己为好友");
        }
        if (userMapper.selectById(friendId) == null) {
            throw new RuntimeException("用户不存在");
        }
        UserFriend existing = userFriendMapper.selectOne(new LambdaQueryWrapper<UserFriend>()
                .eq(UserFriend::getUserId, userId)
                .eq(UserFriend::getFriendId, friendId));
        if (existing != null) {
            throw new RuntimeException(existing.getStatus() == 1 ? "已经是好友" : "好友申请已发送");
        }
        UserFriend relation = new UserFriend();
        relation.setUserId(userId);
        relation.setFriendId(friendId);
        relation.setStatus(0);

        UserFriend reverse = new UserFriend();
        reverse.setUserId(friendId);
        reverse.setFriendId(userId);
        reverse.setStatus(0);

        userFriendMapper.insert(relation);
        if (userFriendMapper.selectOne(new LambdaQueryWrapper<UserFriend>()
                .eq(UserFriend::getUserId, friendId)
                .eq(UserFriend::getFriendId, userId)) == null) userFriendMapper.insert(reverse);
        return Result.success();
    }

    @GetMapping("/requests")
    public Result<List<Map<String, Object>>> listRequests() {
        Long userId = UserContext.getCurrentUserId();
        List<UserFriend> relations = userFriendMapper.selectList(new LambdaQueryWrapper<UserFriend>()
                .eq(UserFriend::getFriendId, userId)
                .eq(UserFriend::getStatus, 0));
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (UserFriend relation : relations) {
            User requester = userMapper.selectById(relation.getUserId());
            if (requester == null) continue;
            Map<String, Object> item = new HashMap<>();
            item.put("id", relation.getId());
            item.put("userId", requester.getId());
            item.put("username", requester.getUsername());
            item.put("nickname", requester.getNickname());
            result.add(item);
        }
        return Result.success(result);
    }

    @PostMapping("/requests/{requestId}/approve")
    public Result<Void> approveRequest(@PathVariable Long requestId) {
        Long userId = UserContext.getCurrentUserId();
        UserFriend incoming = userFriendMapper.selectById(requestId);
        if (incoming == null || !userId.equals(incoming.getFriendId()) || incoming.getStatus() != 0) {
            throw new RuntimeException("好友申请不存在或已处理");
        }
        incoming.setStatus(1);
        userFriendMapper.updateById(incoming);
        UserFriend outgoing = userFriendMapper.selectOne(new LambdaQueryWrapper<UserFriend>()
                .eq(UserFriend::getUserId, userId)
                .eq(UserFriend::getFriendId, incoming.getUserId()));
        if (outgoing != null) {
            outgoing.setStatus(1);
            userFriendMapper.updateById(outgoing);
        }
        return Result.success();
    }

    @GetMapping("/chat/{friendId}")
    public Result<List<Map<String, Object>>> listChatMessages(@PathVariable Long friendId) {
        Long userId = UserContext.getCurrentUserId();
        ensureFriends(userId, friendId);
        List<ChatMessage> messages = chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .and(wrapper -> wrapper
                        .and(pair -> pair.eq(ChatMessage::getSenderId, userId).eq(ChatMessage::getReceiverId, friendId))
                        .or(pair -> pair.eq(ChatMessage::getSenderId, friendId).eq(ChatMessage::getReceiverId, userId)))
                .orderByAsc(ChatMessage::getCreateTime));
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (ChatMessage message : messages) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", message.getId());
            item.put("senderId", message.getSenderId());
            item.put("receiverId", message.getReceiverId());
            item.put("content", secretCryptoService.decrypt(message.getContent()));
            item.put("createTime", message.getCreateTime());
            result.add(item);
        }
        return Result.success(result);
    }

    @PostMapping("/chat/{friendId}")
    public Result<Map<String, Object>> sendChatMessage(@PathVariable Long friendId, @RequestBody Map<String, Object> body) {
        Long userId = UserContext.getCurrentUserId();
        ensureFriends(userId, friendId);
        String content = String.valueOf(body.getOrDefault("content", "")).trim();
        if (content.isBlank()) throw new BusinessException(400, "消息内容不能为空");
        if (content.length() > 5000) throw new BusinessException(400, "消息不能超过 5000 个字符");
        ChatMessage message = new ChatMessage();
        message.setSenderId(userId);
        message.setReceiverId(friendId);
        message.setContent(secretCryptoService.encrypt(content));
        chatMessageMapper.insert(message);
        Map<String, Object> result = new HashMap<>();
        result.put("id", message.getId());
        result.put("senderId", userId);
        result.put("receiverId", friendId);
        result.put("content", content);
        result.put("createTime", message.getCreateTime());
        return Result.success(result);
    }

    private void ensureFriends(Long userId, Long friendId) {
        if (userId.equals(friendId)) throw new BusinessException(400, "不能与自己聊天");
        boolean connected = userFriendMapper.selectCount(new LambdaQueryWrapper<UserFriend>()
                .eq(UserFriend::getUserId, userId)
                .eq(UserFriend::getFriendId, friendId)
                .eq(UserFriend::getStatus, 1)) > 0
                && userFriendMapper.selectCount(new LambdaQueryWrapper<UserFriend>()
                .eq(UserFriend::getUserId, friendId)
                .eq(UserFriend::getFriendId, userId)
                .eq(UserFriend::getStatus, 1)) > 0;
        if (!connected) throw new BusinessException(403, "同意好友申请后才能聊天");
    }

    @GetMapping("/search")
    public Result<List<Map<String, Object>>> searchUsers(@RequestParam String keyword) {
        Long userId = UserContext.getCurrentUserId();
        List<User> users = userMapper.selectList(
                new LambdaQueryWrapper<User>()
                        .like(User::getUsername, keyword)
                        .or().like(User::getNickname, keyword)
                        .ne(User::getId, userId)
        );
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (User user : users) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", user.getId());
            item.put("username", user.getUsername());
            item.put("nickname", user.getNickname());
            result.add(item);
        }
        return Result.success(result);
    }
}
