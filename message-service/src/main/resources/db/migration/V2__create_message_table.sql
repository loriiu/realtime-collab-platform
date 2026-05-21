CREATE TABLE IF NOT EXISTS messages (
    id BIGINT PRIMARY KEY COMMENT 'Snowflake ID',
    session_id BIGINT NOT NULL COMMENT '会话ID',
    sender_id BIGINT NOT NULL COMMENT '发送者',
    receiver_id BIGINT NOT NULL COMMENT '接收者',
    content TEXT COMMENT '消息内容',
    type VARCHAR(20) DEFAULT 'TEXT' COMMENT 'TEXT/IMAGE/SYSTEM',
    version INT DEFAULT 1 COMMENT '消息协议版本',
    status TINYINT DEFAULT 0 COMMENT '0发送中 1送达 2已读',
    is_deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_session_id (session_id),
    INDEX idx_receiver_id (receiver_id),
    INDEX idx_create_time (create_time)
) COMMENT='消息表';
