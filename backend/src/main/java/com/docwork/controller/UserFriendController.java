package com.docwork.controller;

import com.docwork.common.Result;
import com.docwork.entity.User;
import com.docwork.entity.UserFriend;
import com.docwork.interceptor.UserContext;
import com.docwork.mapper.UserMapper;
import com.docwork.mapper.UserFriendMapper;
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
        UserFriend relation = new UserFriend();
        relation.setUserId(userId);
        relation.setFriendId(friendId);
        relation.setStatus(1);

        UserFriend reverse = new UserFriend();
        reverse.setUserId(friendId);
        reverse.setFriendId(userId);
        reverse.setStatus(1);

        if (userFriendMapper.selectOne(new LambdaQueryWrapper<UserFriend>()
                .eq(UserFriend::getUserId, userId)
                .eq(UserFriend::getFriendId, friendId)) == null) {
            userFriendMapper.insert(relation);
        }
        if (userFriendMapper.selectOne(new LambdaQueryWrapper<UserFriend>()
                .eq(UserFriend::getUserId, friendId)
                .eq(UserFriend::getFriendId, userId)) == null) {
            userFriendMapper.insert(reverse);
        }
        return Result.success();
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
