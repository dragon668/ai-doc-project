package com.docwork.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.docwork.common.BusinessException;
import com.docwork.entity.Folder;
import com.docwork.mapper.FolderMapper;
import com.docwork.service.FolderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FolderServiceImpl implements FolderService {

    private final FolderMapper folderMapper;

    @Override
    public Folder createFolder(String name, Long parentId, Long workspaceId, Long creatorId) {
        Folder folder = new Folder();
        folder.setName(name);
        folder.setParentId(parentId != null ? parentId : 0L);
        folder.setWorkspaceId(workspaceId);
        folder.setCreatorId(creatorId);
        folderMapper.insert(folder);
        return folder;
    }

    @Override
    public List<Folder> listFolders(Long workspaceId, Long parentId) {
        return folderMapper.selectList(
                new LambdaQueryWrapper<Folder>()
                        .eq(Folder::getWorkspaceId, workspaceId)
                        .eq(Folder::getParentId, parentId != null ? parentId : 0L)
                        .eq(Folder::getDeleted, 0)
                        .orderByAsc(Folder::getName)
        );
    }

    @Override
    public void renameFolder(Long folderId, String newName, Long userId) {
        Folder folder = folderMapper.selectById(folderId);
        if (folder == null || folder.getDeleted() == 1) {
            throw new BusinessException(404, "文件夹不存在");
        }
        folder.setName(newName);
        folderMapper.updateById(folder);
    }

    @Override
    public void deleteFolder(Long folderId, Long userId) {
        Folder folder = folderMapper.selectById(folderId);
        if (folder == null || folder.getDeleted() == 1) {
            throw new BusinessException(404, "文件夹不存在");
        }
        folderMapper.deleteById(folderId);
    }
}
