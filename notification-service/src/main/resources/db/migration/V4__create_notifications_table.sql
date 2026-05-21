CREATE TABLE IF NOT EXISTS notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
    user_id BIGINT NOT NULL COMMENT '目标用户ID',
    type VARCHAR(30) NOT NULL DEFAULT 'MESSAGE' COMMENT '通知类型: MESSAGE/FILE_UPLOADED/SYSTEM',
    title VARCHAR(200) NOT NULL DEFAULT '' COMMENT '通知标题',
    content TEXT COMMENT '通知内容',
    source_id VARCHAR(100) COMMENT '来源实体ID (如消息ID/文件ID)',
    source_type VARCHAR(30) COMMENT '来源类型: message/file',
    is_read TINYINT DEFAULT 0 COMMENT '0未读 1已读',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_user_id (user_id),
    INDEX idx_user_read (user_id, is_read),
    INDEX idx_create_time (create_time)
) COMMENT='通知表';
