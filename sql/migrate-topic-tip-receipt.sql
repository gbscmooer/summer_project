-- Topic tip receipt for idempotent tip_total updates after points transfer.
USE campus_trade;

CREATE TABLE IF NOT EXISTS t_topic_tip_receipt (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    post_id     BIGINT       NOT NULL COMMENT '帖子ID',
    tipper_id   BIGINT       NOT NULL COMMENT '打赏人ID',
    amount      INT          NOT NULL COMMENT '打赏积分',
    request_id  VARCHAR(64)  NOT NULL COMMENT '客户端幂等键',
    status      TINYINT      NOT NULL DEFAULT 0 COMMENT '0-处理中 1-已完成',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_request_id (request_id),
    INDEX idx_post_id (post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='话题打赏收据';
