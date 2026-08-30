package com.docwork.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.docwork.common.BusinessException;
import com.docwork.common.Constants;
import com.docwork.entity.Workspace;
import com.docwork.entity.WorkspaceMember;
import com.docwork.mapper.WorkspaceMapper;
import com.docwork.mapper.WorkspaceMemberMapper;
import com.docwork.service.WorkspaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkspaceServiceImpl implements WorkspaceService {

    private final WorkspaceMapper workspaceMapper;
    private final WorkspaceMemberMapper memberMapper;

    @Override
    @Transactional
    public Workspace createWorkspace(String name, String description, Long ownerId) {
        Workspace ws = new Workspace();
        ws.setName(name);
        ws.setDescription(description != null ? description : "");
        ws.setOwnerId(ownerId);
        ws.setType(2); // 团队空间
        workspaceMapper.insert(ws);

        WorkspaceMember member = new WorkspaceMember();
        member.setWorkspaceId(ws.getId());
        member.setUserId(ownerId);
        member.setRole(Constants.ROLE_OWNER);
        memberMapper.insert(member);

        return ws;
    }

    @Override
    public List<Workspace> listUserWorkspaces(Long userId) {
        List<WorkspaceMember> memberships = memberMapper.selectList(
                new LambdaQueryWrapper<WorkspaceMember>()
                        .eq(WorkspaceMember::getUserId, userId)
        );
        List<Long> wsIds = memberships.stream()
                .map(WorkspaceMember::getWorkspaceId)
                .collect(Collectors.toList());

        if (wsIds.isEmpty()) return List.of();

        return workspaceMapper.selectBatchIds(wsIds);
    }

    @Override
    public Workspace getWorkspace(Long workspaceId) {
        Workspace ws = workspaceMapper.selectById(workspaceId);
        if (ws == null || ws.getDeleted() == 1) {
            throw new BusinessException(404, "空间不存在");
        }
        return ws;
    }

    @Override
    public void updateWorkspace(Long workspaceId, String name, String description, Long userId) {
        int role = checkPermission(workspaceId, userId);
        if (role > Constants.ROLE_ADMIN) {
            throw new BusinessException(403, "无权修改空间信息");
        }
        Workspace ws = getWorkspace(workspaceId);
        if (name != null) ws.setName(name);
        if (description != null) ws.setDescription(description);
        workspaceMapper.updateById(ws);
    }

    @Override
    public void deleteWorkspace(Long workspaceId, Long userId) {
        int role = checkPermission(workspaceId, userId);
        if (role != Constants.ROLE_OWNER) {
            throw new BusinessException(403, "只有空间所有者可以删除空间");
        }
        workspaceMapper.deleteById(workspaceId);
    }

    @Override
    public void addMember(Long workspaceId, Long userId, int role, Long operatorId) {
        int operatorRole = checkPermission(workspaceId, operatorId);
        if (operatorRole > Constants.ROLE_ADMIN) {
            throw new BusinessException(403, "无权添加成员");
        }

        Long count = memberMapper.selectCount(
                new LambdaQueryWrapper<WorkspaceMember>()
                        .eq(WorkspaceMember::getWorkspaceId, workspaceId)
                        .eq(WorkspaceMember::getUserId, userId)
        );
        if (count > 0) {
            throw new BusinessException("该用户已是空间成员");
        }

        WorkspaceMember member = new WorkspaceMember();
        member.setWorkspaceId(workspaceId);
        member.setUserId(userId);
        member.setRole(role);
        memberMapper.insert(member);
    }

    @Override
    public void removeMember(Long workspaceId, Long userId, Long operatorId) {
        int operatorRole = checkPermission(workspaceId, operatorId);
        if (operatorRole > Constants.ROLE_ADMIN) {
            throw new BusinessException(403, "无权移除成员");
        }

        memberMapper.delete(
                new LambdaQueryWrapper<WorkspaceMember>()
                        .eq(WorkspaceMember::getWorkspaceId, workspaceId)
                        .eq(WorkspaceMember::getUserId, userId)
        );
    }

    @Override
    public List<WorkspaceMember> listMembers(Long workspaceId) {
        return memberMapper.selectList(
                new LambdaQueryWrapper<WorkspaceMember>()
                        .eq(WorkspaceMember::getWorkspaceId, workspaceId)
        );
    }

    @Override
    public int checkPermission(Long workspaceId, Long userId) {
        WorkspaceMember member = memberMapper.selectOne(
                new LambdaQueryWrapper<WorkspaceMember>()
                        .eq(WorkspaceMember::getWorkspaceId, workspaceId)
                        .eq(WorkspaceMember::getUserId, userId)
        );
        if (member == null) {
            throw new BusinessException(403, "您不是该空间的成员");
        }
        return member.getRole();
    }
}
