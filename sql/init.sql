-- ============================================================
-- realtime-collab-platform — Database Initialization
-- ============================================================

CREATE DATABASE IF NOT EXISTS collab_platform
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE collab_platform;

-- Users table
CREATE TABLE IF NOT EXISTS users (
    id          BIGINT       NOT NULL AUTO_INCREMENT  COMMENT 'Primary key',
    username    VARCHAR(50)  NOT NULL                 COMMENT 'Unique username',
    password    VARCHAR(255) NOT NULL                 COMMENT 'BCrypt-encoded password',
    nickname    VARCHAR(100) DEFAULT NULL             COMMENT 'Display name',
    avatar      VARCHAR(255) DEFAULT NULL             COMMENT 'Avatar URL',
    status      TINYINT      NOT NULL DEFAULT 1       COMMENT '1=active, 0=disabled',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='User accounts';
