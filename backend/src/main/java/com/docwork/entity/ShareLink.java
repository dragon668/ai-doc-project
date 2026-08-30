package com.docwork.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("share_link")
public class ShareLink {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private Long documentId;
    private Long creatorId;
    private Integer permission;
    private LocalDateTime expireTime;
    private Integer viewCount;
    private Integer maxViews;
    private String password;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
}
