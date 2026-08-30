package com.docwork.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ChunkUploadDTO {
    @NotBlank(message = "上传ID不能为空")
    private String uploadId;
    @NotBlank(message = "文件名不能为空")
    private String fileName;
    @NotBlank(message = "文件MD5不能为空")
    private String md5;
    @NotNull(message = "分片序号不能为空")
    private Integer chunkIndex;
    @NotNull(message = "总分片数不能为空")
    private Integer totalChunks;
    private String chunkMd5;
}
