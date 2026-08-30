-- ============================================================
-- AI文档协作工作台 - 数据库初始化脚本
-- ============================================================

CREATE DATABASE IF NOT EXISTS doc_workspace DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE doc_workspace;

-- 用户表
CREATE TABLE IF NOT EXISTS `user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `username` VARCHAR(50) NOT NULL COMMENT '用户名',
    `password` VARCHAR(255) NOT NULL COMMENT '密码(BCrypt加密)',
    `nickname` VARCHAR(50) DEFAULT '' COMMENT '昵称',
    `avatar` VARCHAR(500) DEFAULT '' COMMENT '头像URL',
    `email` VARCHAR(100) DEFAULT '' COMMENT '邮箱',
    `used_storage` BIGINT DEFAULT 0 COMMENT '已用存储空间(字节)',
    `total_storage` BIGINT DEFAULT 10737418240 COMMENT '总存储空间(字节),默认10GB',
    `status` TINYINT DEFAULT 1 COMMENT '状态:0-禁用,1-正常',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除:0-未删,1-已删',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 工作空间表
CREATE TABLE IF NOT EXISTS `workspace` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '空间ID',
    `name` VARCHAR(100) NOT NULL COMMENT '空间名称',
    `description` VARCHAR(500) DEFAULT '' COMMENT '空间描述',
    `owner_id` BIGINT NOT NULL COMMENT '创建者/所有者用户ID',
    `type` TINYINT DEFAULT 1 COMMENT '类型:1-个人,2-团队',
    `deleted` TINYINT DEFAULT 0,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_owner` (`owner_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作空间表';

-- 空间成员表(两级权限模型)
CREATE TABLE IF NOT EXISTS `workspace_member` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `workspace_id` BIGINT NOT NULL COMMENT '空间ID',
    `user_id` BIGINT NOT NULL COMMENT '成员用户ID',
    `role` TINYINT DEFAULT 2 COMMENT '角色:0-所有者,1-管理员,2-编辑者,3-只读',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ws_user` (`workspace_id`, `user_id`),
    KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='空间成员表';

-- 文件夹表
CREATE TABLE IF NOT EXISTS `folder` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(200) NOT NULL COMMENT '文件夹名称',
    `parent_id` BIGINT DEFAULT 0 COMMENT '父文件夹ID,0为根目录',
    `workspace_id` BIGINT NOT NULL COMMENT '所属空间ID',
    `creator_id` BIGINT NOT NULL COMMENT '创建者ID',
    `deleted` TINYINT DEFAULT 0,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_ws_parent` (`workspace_id`, `parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件夹表';

-- 文档表
CREATE TABLE IF NOT EXISTS `document` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `title` VARCHAR(300) NOT NULL COMMENT '文档标题',
    `type` VARCHAR(20) NOT NULL COMMENT '文档类型:pdf,markdown,docx',
    `file_size` BIGINT DEFAULT 0 COMMENT '文件大小(字节)',
    `file_key` VARCHAR(500) DEFAULT '' COMMENT 'MinIO对象键',
    `md5` VARCHAR(32) DEFAULT '' COMMENT '文件MD5',
    `folder_id` BIGINT DEFAULT 0 COMMENT '所属文件夹ID',
    `workspace_id` BIGINT NOT NULL COMMENT '所属空间ID',
    `creator_id` BIGINT NOT NULL COMMENT '上传者ID',
    `version` INT DEFAULT 1 COMMENT '当前版本号',
    `status` TINYINT DEFAULT 0 COMMENT '状态:0-上传中,1-正常,2-解析中,3-已向量化,4-解析失败',
    `permission` TINYINT DEFAULT 1 COMMENT '文档权限:0-私有,1-空间可见',
    `deleted` TINYINT DEFAULT 0,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_ws_folder` (`workspace_id`, `folder_id`),
    KEY `idx_creator` (`creator_id`),
    KEY `idx_md5` (`md5`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档表';

-- 文档版本表
CREATE TABLE IF NOT EXISTS `document_version` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `document_id` BIGINT NOT NULL COMMENT '文档ID',
    `version` INT NOT NULL COMMENT '版本号',
    `file_key` VARCHAR(500) NOT NULL COMMENT '该版本的MinIO对象键',
    `file_size` BIGINT DEFAULT 0,
    `remark` VARCHAR(500) DEFAULT '' COMMENT '版本备注',
    `operator_id` BIGINT NOT NULL COMMENT '操作人ID',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_doc_ver` (`document_id`, `version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档版本表';

-- 分片上传记录表
CREATE TABLE IF NOT EXISTS `upload_chunk` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `upload_id` VARCHAR(64) NOT NULL COMMENT '上传任务ID',
    `file_name` VARCHAR(300) NOT NULL,
    `md5` VARCHAR(32) NOT NULL COMMENT '整个文件的MD5',
    `chunk_index` INT NOT NULL COMMENT '分片序号',
    `chunk_md5` VARCHAR(32) DEFAULT '' COMMENT '分片MD5',
    `chunk_size` BIGINT DEFAULT 0,
    `total_chunks` INT NOT NULL COMMENT '总分片数',
    `file_key` VARCHAR(500) DEFAULT '' COMMENT 'MinIO中的分片存储键',
    `user_id` BIGINT NOT NULL,
    `status` TINYINT DEFAULT 0 COMMENT '0-上传中,1-已完成,2-已合并',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_upload_id` (`upload_id`),
    KEY `idx_md5` (`md5`),
    KEY `idx_user_status` (`user_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分片上传记录表';

-- 分享链接表
CREATE TABLE IF NOT EXISTS `share_link` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `code` VARCHAR(32) NOT NULL COMMENT '分享码',
    `document_id` BIGINT NOT NULL COMMENT '文档ID',
    `creator_id` BIGINT NOT NULL COMMENT '创建者ID',
    `permission` TINYINT DEFAULT 1 COMMENT '分享权限:1-可查看,2-可编辑',
    `expire_time` DATETIME NOT NULL COMMENT '过期时间',
    `view_count` INT DEFAULT 0 COMMENT '查看次数',
    `max_views` INT DEFAULT -1 COMMENT '最大查看次数,-1为不限',
    `password` VARCHAR(50) DEFAULT '' COMMENT '提取码,空则无需提取码',
    `deleted` TINYINT DEFAULT 0,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`code`),
    KEY `idx_doc` (`document_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分享链接表';

-- 好友关系表
CREATE TABLE IF NOT EXISTS `user_friend` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `friend_id` BIGINT NOT NULL COMMENT '好友ID',
    `status` TINYINT DEFAULT 1 COMMENT '状态:1-已添加,0-待审核',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_friend` (`user_id`, `friend_id`),
    KEY `idx_user` (`user_id`),
    KEY `idx_friend` (`friend_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='好友关系表';

-- AI接口配置表
CREATE TABLE IF NOT EXISTS `ai_api_config` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `provider` VARCHAR(50) NOT NULL DEFAULT 'openai' COMMENT '服务商',
    `api_key` VARCHAR(500) DEFAULT '' COMMENT 'API Key',
    `base_url` VARCHAR(500) DEFAULT '' COMMENT 'Base URL',
    `model_name` VARCHAR(100) DEFAULT 'gpt-4o-mini' COMMENT '模型名称',
    `is_default` TINYINT DEFAULT 0 COMMENT '是否默认',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI接口配置表';

-- AI对话表
CREATE TABLE IF NOT EXISTS `ai_conversation` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `workspace_id` BIGINT NOT NULL COMMENT '关联空间(知识库范围)',
    `title` VARCHAR(200) DEFAULT '新对话',
    `deleted` TINYINT DEFAULT 0,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_ws` (`user_id`, `workspace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI对话表';

-- AI对话消息表
CREATE TABLE IF NOT EXISTS `ai_message` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `conversation_id` BIGINT NOT NULL,
    `role` VARCHAR(20) NOT NULL COMMENT 'user/assistant/system',
    `content` TEXT NOT NULL COMMENT '消息内容',
    `reference_docs` JSON DEFAULT NULL COMMENT '引用的文档列表',
    `token_count` INT DEFAULT 0,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_conv` (`conversation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI对话消息表';

-- 操作日志表
CREATE TABLE IF NOT EXISTS `operation_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `module` VARCHAR(50) NOT NULL COMMENT '模块',
    `action` VARCHAR(50) NOT NULL COMMENT '操作类型',
    `target_type` VARCHAR(50) DEFAULT '' COMMENT '操作对象类型',
    `target_id` BIGINT DEFAULT 0 COMMENT '操作对象ID',
    `detail` VARCHAR(1000) DEFAULT '' COMMENT '详情',
    `ip` VARCHAR(50) DEFAULT '',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_time` (`user_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志表';

-- ============================================================
-- 初始测试数据
-- ============================================================

-- 测试用户 (密码均为: 123456, BCrypt加密)
INSERT INTO `user` (`username`, `password`, `nickname`, `email`, `used_storage`, `total_storage`, `status`)
VALUES
('admin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '管理员', 'admin@docwork.com', 0, 10737418240, 1),
('testuser', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '测试用户', 'test@docwork.com', 0, 10737418240, 1);

-- 管理员的个人空间
INSERT INTO `workspace` (`name`, `description`, `owner_id`, `type`)
VALUES ('管理员的个人空间', '系统管理员的默认空间', 1, 1);

-- 测试用户的个人空间
INSERT INTO `workspace` (`name`, `description`, `owner_id`, `type`)
VALUES ('测试用户的个人空间', '测试用户的默认空间', 2, 1);

-- 空间成员关系
INSERT INTO `workspace_member` (`workspace_id`, `user_id`, `role`) VALUES (1, 1, 0);
INSERT INTO `workspace_member` (`workspace_id`, `user_id`, `role`) VALUES (2, 2, 0);

