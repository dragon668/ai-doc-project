package com.docwork.common;

/**
 * 常量定义
 */
public final class Constants {

    private Constants() {}

    // 用户状态
    public static final int USER_DISABLED = 0;
    public static final int USER_NORMAL = 1;

    // 空间角色
    public static final int ROLE_OWNER = 0;
    public static final int ROLE_ADMIN = 1;
    public static final int ROLE_EDITOR = 2;
    public static final int ROLE_VIEWER = 3;

    // 文档状态
    public static final int DOC_UPLOADING = 0;
    public static final int DOC_NORMAL = 1;
    public static final int DOC_PARSING = 2;
    public static final int DOC_VECTORIZED = 3;
    public static final int DOC_PARSE_FAILED = 4;

    // 文档权限
    public static final int DOC_PRIVATE = 0;
    public static final int DOC_SPACE_VISIBLE = 1;

    // 上传状态
    public static final int UPLOAD_IN_PROGRESS = 0;
    public static final int UPLOAD_COMPLETED = 1;
    public static final int UPLOAD_MERGED = 2;

    // Redis Key前缀
    public static final String REDIS_USER_TOKEN = "user:token:";
    public static final String REDIS_UPLOAD_CHUNKS = "upload:chunks:";
    public static final String REDIS_DOC_MD5 = "doc:md5:";
    public static final String REDIS_QUOTA = "quota:used:";
    public static final String REDIS_SHARE_CODE = "share:code:";
    public static final String REDIS_AI_CONVERSATION = "ai:conv:";
    public static final String REDIS_RATE_LIMIT = "rate:limit:";
    public static final String REDIS_SENTINEL = "sentinel:";

    // MQ Topic
    public static final String MQ_TOPIC_DOC_PARSE = "DOC_PARSE_TOPIC";
    public static final String MQ_TAG_VECTORIZE = "VECTORIZE";

    // 默认分片大小 5MB
    public static final long DEFAULT_CHUNK_SIZE = 5 * 1024 * 1024;
}
