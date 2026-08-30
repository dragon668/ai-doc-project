package com.docwork.service;

import com.docwork.entity.Folder;
import java.util.List;

public interface FolderService {
    Folder createFolder(String name, Long parentId, Long workspaceId, Long creatorId);
    List<Folder> listFolders(Long workspaceId, Long parentId);
    void renameFolder(Long folderId, String newName, Long userId);
    void deleteFolder(Long folderId, Long userId);
}
