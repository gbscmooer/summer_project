-- 好友申请 / 好友关系 / 私信会话与消息
-- 可对已有库执行；新环境由 init.sql 同步建表

CREATE TABLE IF NOT EXISTS t_friend_request (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    from_user_id BIGINT NOT NULL COMMENT '申请人',
    to_user_id   BIGINT NOT NULL COMMENT '被申请人',
    status      TINYINT NOT NULL DEFAULT 0 COMMENT '0-待处理 1-已同意 2-已拒绝',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_from_to (from_user_id, to_user_id),
    INDEX idx_to_status (to_user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='好友申请';

CREATE TABLE IF NOT EXISTS t_friendship (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_low_id  BIGINT NOT NULL COMMENT '较小用户ID',
    user_high_id BIGINT NOT NULL COMMENT '较大用户ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_pair (user_low_id, user_high_id),
    INDEX idx_high (user_high_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='双向好友关系';

CREATE TABLE IF NOT EXISTS t_conversation (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_low_id  BIGINT NOT NULL,
    user_high_id BIGINT NOT NULL,
    last_msg_preview VARCHAR(200) DEFAULT NULL,
    last_message_at DATETIME DEFAULT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_pair (user_low_id, user_high_id),
    INDEX idx_last_msg (last_message_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='私信会话';

CREATE TABLE IF NOT EXISTS t_message (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    conversation_id BIGINT NOT NULL,
    sender_id   BIGINT NOT NULL,
    content     VARCHAR(2000) NOT NULL,
    is_read     TINYINT NOT NULL DEFAULT 0 COMMENT '对接收方是否已读',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_conv_time (conversation_id, create_time),
    INDEX idx_sender (sender_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='私信消息';
