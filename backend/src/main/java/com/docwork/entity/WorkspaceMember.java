package com.docwork.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("workspace_member")
public class WorkspaceMember {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long workspaceId;
    private Long userId;
    /** 角色:0-所有者,1-管理员,2-编辑者,3-只读 */
    private Integer role;
    private LocalDateTime createTime;
}
