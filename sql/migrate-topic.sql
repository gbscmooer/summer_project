-- Optional upgrade: topic posts with optional attached products.
USE campus_trade;

CREATE TABLE IF NOT EXISTS t_topic_post (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '帖子ID',
    user_id       BIGINT       NOT NULL COMMENT '作者用户ID',
    title         VARCHAR(100) NOT NULL COMMENT '标题',
    content       TEXT         NOT NULL COMMENT '正文',
    upvote_count  INT          NOT NULL DEFAULT 0 COMMENT '点赞数',
    comment_count INT          NOT NULL DEFAULT 0 COMMENT '评论数',
    create_time   DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
    update_time   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='话题帖子表';

CREATE TABLE IF NOT EXISTS t_topic_post_product (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '关联ID',
    post_id     BIGINT NOT NULL COMMENT '帖子ID',
    product_id  BIGINT NOT NULL COMMENT '商品ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_post_product (post_id, product_id),
    INDEX idx_post_id (post_id),
    INDEX idx_product_id (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='帖子附带商品关联表';

-- Nested comments / votes: see migrate-topic-reddit.sql for upgrades on existing DBs.