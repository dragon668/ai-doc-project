package com.docwork.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("document_version")
public class DocumentVersion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long documentId;
    private Integer version;
    private String fileKey;
    private Long fileSize;
    private String remark;
    private Long operatorId;
    private LocalDateTime createTime;
}
