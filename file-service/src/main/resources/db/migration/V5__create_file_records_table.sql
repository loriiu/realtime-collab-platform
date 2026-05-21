CREATE TABLE IF NOT EXISTS file_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
    file_id VARCHAR(64) NOT NULL UNIQUE COMMENT '对外文件唯一标识(UUID)',
    original_name VARCHAR(500) NOT NULL COMMENT '原始文件名',
    object_key VARCHAR(500) NOT NULL COMMENT 'MinIO对象键(路径)',
    bucket VARCHAR(100) NOT NULL DEFAULT 'collab-files' COMMENT 'MinIO 桶名',
    content_type VARCHAR(100) COMMENT 'MIME类型',
    size_bytes BIGINT DEFAULT 0 COMMENT '文件大小(字节)',
    uploader_id BIGINT NOT NULL COMMENT '上传者用户ID',
    is_deleted TINYINT DEFAULT 0 COMMENT '0正常 1已删除',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_file_id (file_id),
    INDEX idx_uploader_id (uploader_id),
    INDEX idx_is_deleted (is_deleted)
) COMMENT='文件记录表';
