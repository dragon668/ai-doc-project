package com.docwork.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.docwork.common.BusinessException;
import com.docwork.common.Constants;
import com.docwork.dto.LoginDTO;
import com.docwork.dto.LoginVO;
import com.docwork.dto.RegisterDTO;
import com.docwork.entity.User;
import com.docwork.entity.Workspace;
import com.docwork.entity.WorkspaceMember;
import com.docwork.mapper.UserMapper;
import com.docwork.mapper.WorkspaceMapper;
import com.docwork.mapper.WorkspaceMemberMapper;
import com.docwork.security.JwtUtils;
import com.docwork.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final WorkspaceMapper workspaceMapper;
    private final WorkspaceMemberMapper memberMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final StringRedisTemplate redisTemplate;

    @Override
    public LoginVO login(LoginDTO dto) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, dto.getUsername())
                        .eq(User::getDeleted, 0)
        );
        if (user == null || !passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BusinessException(401, "用户名或密码错误");
        }
        if (user.getStatus() != Constants.USER_NORMAL) {
            throw new BusinessException(403, "账号已被禁用");
        }

        String token = jwtUtils.generateToken(user.getId(), user.getUsername());
        String refreshToken = jwtUtils.generateRefreshToken(user.getId());

        LoginVO vo = new LoginVO();
        vo.setToken(token);
        vo.setRefreshToken(refreshToken);
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        return vo;
    }

    @Override
    @Transactional
    public void register(RegisterDTO dto) {
        // 检查用户名唯一
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, dto.getUsername())
        );
        if (count > 0) {
            throw new BusinessException(400, "用户名已存在");
        }

        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setNickname(dto.getNickname() != null ? dto.getNickname() : dto.getUsername());
        user.setEmail(dto.getEmail() != null ? dto.getEmail() : "");
        user.setUsedStorage(0L);
        user.setTotalStorage(10737418240L); // 10GB
        user.setStatus(Constants.USER_NORMAL);
        userMapper.insert(user);

        // 自动创建个人空间
        Workspace workspace = new Workspace();
        workspace.setName(user.getNickname() + "的个人空间");
        workspace.setDescription("个人文档空间");
        workspace.setOwnerId(user.getId());
        workspace.setType(1);
        workspaceMapper.insert(workspace);

        // 添加为空间所有者
        WorkspaceMember member = new WorkspaceMember();
        member.setWorkspaceId(workspace.getId());
        member.setUserId(user.getId());
        member.setRole(Constants.ROLE_OWNER);
        memberMapper.insert(member);

        log.info("用户注册成功: {}", dto.getUsername());
    }

    @Override
    public LoginVO refreshToken(String refreshToken) {
        if (!jwtUtils.validateToken(refreshToken)) {
            throw new BusinessException(401, "Refresh Token已过期，请重新登录");
        }
        Long userId = jwtUtils.getUserIdFromToken(refreshToken);
        User user = userMapper.selectById(userId);
        if (user == null || user.getStatus() != Constants.USER_NORMAL) {
            throw new BusinessException(401, "用户不存在或已禁用");
        }

        String newToken = jwtUtils.generateToken(user.getId(), user.getUsername());
        String newRefreshToken = jwtUtils.generateRefreshToken(user.getId());

        LoginVO vo = new LoginVO();
        vo.setToken(newToken);
        vo.setRefreshToken(newRefreshToken);
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        return vo;
    }

    @Override
    public void logout(String token) {
        // 将Token加入Redis黑名单，直到过期
        if (jwtUtils.validateToken(token)) {
            long remaining = jwtUtils.parseToken(token).getExpiration().getTime() - System.currentTimeMillis();
            if (remaining > 0) {
                redisTemplate.opsForValue().set(
                        Constants.REDIS_USER_TOKEN + token,
                        "blacklisted",
                        remaining, TimeUnit.MILLISECONDS
                );
            }
        }
    }
}
