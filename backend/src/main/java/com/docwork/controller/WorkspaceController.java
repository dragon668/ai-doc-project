package com.docwork.controller;

import com.docwork.common.Result;
import com.docwork.entity.Workspace;
import com.docwork.entity.WorkspaceMember;
import com.docwork.interceptor.UserContext;
import com.docwork.service.WorkspaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/workspace")
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    @PostMapping
    public Result<Workspace> create(@RequestBody Map<String, String> body) {
        Long userId = UserContext.getCurrentUserId();
        return Result.success(workspaceService.createWorkspace(
                body.get("name"), body.get("description"), userId));
    }

    @GetMapping("/list")
    public Result<List<Workspace>> list() {
        Long userId = UserContext.getCurrentUserId();
        return Result.success(workspaceService.listUserWorkspaces(userId));
    }

    @GetMapping("/{workspaceId}")
    public Result<Workspace> get(@PathVariable Long workspaceId) {
        return Result.success(workspaceService.getWorkspace(workspaceId));
    }

    @PutMapping("/{workspaceId}")
    public Result<Void> update(@PathVariable Long workspaceId, @RequestBody Map<String, String> body) {
        Long userId = UserContext.getCurrentUserId();
        workspaceService.updateWorkspace(workspaceId, body.get("name"), body.get("description"), userId);
        return Result.success();
    }

    @DeleteMapping("/{workspaceId}")
    public Result<Void> delete(@PathVariable Long workspaceId) {
        Long userId = UserContext.getCurrentUserId();
        workspaceService.deleteWorkspace(workspaceId, userId);
        return Result.success();
    }

    @GetMapping("/{workspaceId}/members")
    public Result<List<WorkspaceMember>> listMembers(@PathVariable Long workspaceId) {
        return Result.success(workspaceService.listMembers(workspaceId));
    }

    @PostMapping("/{workspaceId}/members")
    public Result<Void> addMember(@PathVariable Long workspaceId, @RequestBody Map<String, Object> body) {
        Long userId = UserContext.getCurrentUserId();
        Long targetUserId = Long.parseLong(body.get("userId").toString());
        int role = Integer.parseInt(body.getOrDefault("role", "2").toString());
        workspaceService.addMember(workspaceId, targetUserId, role, userId);
        return Result.success();
    }

    @DeleteMapping("/{workspaceId}/members/{targetUserId}")
    public Result<Void> removeMember(@PathVariable Long workspaceId, @PathVariable Long targetUserId) {
        Long userId = UserContext.getCurrentUserId();
        workspaceService.removeMember(workspaceId, targetUserId, userId);
        return Result.success();
    }
}
