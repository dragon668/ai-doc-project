package com.docwork.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("upload_chunk")
public class UploadChunk {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String uploadId;
    private String fileName;
    private String md5;
    private Integer chunkIndex;
    private String chunkMd5;
    private Long chunkSize;
    private Integer totalChunks;
    private String fileKey;
    private Long userId;
    /** 0-上传中,1-已完成,2-已合并 */
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
