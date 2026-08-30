package com.docwork.service;

import com.docwork.entity.Workspace;
import com.docwork.entity.WorkspaceMember;

import java.util.List;

public interface WorkspaceService {

    Workspace createWorkspace(String name, String description, Long ownerId);
    List<Workspace> listUserWorkspaces(Long userId);
    Workspace getWorkspace(Long workspaceId);
    void updateWorkspace(Long workspaceId, String name, String description, Long userId);
    void deleteWorkspace(Long workspaceId, Long userId);

    /** 添加成员到空间 */
    void addMember(Long workspaceId, Long userId, int role, Long operatorId);
    /** 移除成员 */
    void removeMember(Long workspaceId, Long userId, Long operatorId);
    /** 获取空间成员列表 */
    List<WorkspaceMember> listMembers(Long workspaceId);
    /** 检查用户在空间中的角色 */
    int checkPermission(Long workspaceId, Long userId);
}
