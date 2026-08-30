package com.docwork.controller;

import com.docwork.common.Result;
import com.docwork.entity.Folder;
import com.docwork.interceptor.UserContext;
import com.docwork.service.FolderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/folder")
@RequiredArgsConstructor
public class FolderController {

    private final FolderService folderService;

    @PostMapping
    public Result<Folder> create(@RequestBody Map<String, Object> body) {
        Long userId = UserContext.getCurrentUserId();
        String name = (String) body.get("name");
        Long parentId = body.get("parentId") != null ? Long.parseLong(body.get("parentId").toString()) : 0L;
        Long workspaceId = Long.parseLong(body.get("workspaceId").toString());
        return Result.success(folderService.createFolder(name, parentId, workspaceId, userId));
    }

    @GetMapping("/list")
    public Result<List<Folder>> list(@RequestParam Long workspaceId,
                                     @RequestParam(required = false) Long parentId) {
        return Result.success(folderService.listFolders(workspaceId, parentId));
    }

    @PutMapping("/{folderId}")
    public Result<Void> rename(@PathVariable Long folderId, @RequestBody Map<String, String> body) {
        Long userId = UserContext.getCurrentUserId();
        folderService.renameFolder(folderId, body.get("name"), userId);
        return Result.success();
    }

    @DeleteMapping("/{folderId}")
    public Result<Void> delete(@PathVariable Long folderId) {
        Long userId = UserContext.getCurrentUserId();
        folderService.deleteFolder(folderId, userId);
        return Result.success();
    }
}
