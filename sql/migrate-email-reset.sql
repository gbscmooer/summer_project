-- 用户邮箱与密码重置
USE campus_trade;

ALTER TABLE t_user
    ADD COLUMN email VARCHAR(120) NULL COMMENT '绑定邮箱' AFTER phone,
    ADD COLUMN email_verified TINYINT NOT NULL DEFAULT 0 COMMENT '0-未验证 1-已验证' AFTER email;

CREATE UNIQUE INDEX uk_user_email ON t_user (email);

CREATE TABLE IF NOT EXISTS t_password_reset_token (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    token_hash  CHAR(64)     NOT NULL COMMENT 'SHA-256 hex of raw token',
    expires_at  DATETIME     NOT NULL,
    used_at     DATETIME     NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_token_hash (token_hash),
    INDEX idx_user_id (user_id),
    INDEX idx_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='密码重置令牌';
