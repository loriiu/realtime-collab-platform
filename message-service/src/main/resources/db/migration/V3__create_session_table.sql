CREATE TABLE IF NOT EXISTS sessions (
    id BIGINT PRIMARY KEY COMMENT 'Snowflake ID',
    session_key VARCHAR(100) UNIQUE NOT NULL COMMENT '会话标识',
    user_a BIGINT NOT NULL COMMENT '用户A',
    user_b BIGINT NOT NULL COMMENT '用户B',
    last_message TEXT COMMENT '最后一条消息',
    last_message_time DATETIME COMMENT '最后消息时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_a (user_a),
    INDEX idx_user_b (user_b)
) COMMENT='会话表';
