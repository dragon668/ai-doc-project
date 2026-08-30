package com.docwork.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("document")
public class Document {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String type;
    private Long fileSize;
    private String fileKey;
    private String md5;
    private Long folderId;
    private Long workspaceId;
    private Long creatorId;
    private Integer version;
    /** 状态:0-上传中,1-正常,2-解析中,3-已向量化,4-解析失败 */
    private Integer status;
    /** 文档权限:0-私有,1-空间可见 */
    private Integer permission;
    @TableLogic
    private Integer deleted;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
