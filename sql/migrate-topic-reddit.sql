-- Reddit-style topic enhancement: nested comments, upvotes, counters.
USE campus_trade;

-- Post counters (safe to re-run)
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'campus_trade' AND TABLE_NAME = 't_topic_post' AND COLUMN_NAME = 'upvote_count'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE t_topic_post ADD COLUMN upvote_count INT NOT NULL DEFAULT 0 COMMENT ''点赞数'' AFTER content',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'campus_trade' AND TABLE_NAME = 't_topic_post' AND COLUMN_NAME = 'comment_count'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE t_topic_post ADD COLUMN comment_count INT NOT NULL DEFAULT 0 COMMENT ''评论数'' AFTER upvote_count',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS t_topic_comment (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '评论ID',
    post_id      BIGINT       NOT NULL COMMENT '帖子ID',
    user_id      BIGINT       NOT NULL COMMENT '评论用户ID',
    parent_id    BIGINT       NULL COMMENT '父评论ID，空为顶层',
    content      VARCHAR(1000) NOT NULL COMMENT '评论内容',
    image_url    VARCHAR(255) NULL COMMENT '可选配图 URL',
    upvote_count INT          NOT NULL DEFAULT 0 COMMENT '点赞数',
    create_time  DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '评论时间',
    INDEX idx_post_id (post_id),
    INDEX idx_parent_id (parent_id),
    INDEX idx_user_id (user_id),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='话题评论表';

CREATE TABLE IF NOT EXISTS t_topic_post_vote (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    post_id     BIGINT NOT NULL COMMENT '帖子ID',
    user_id     BIGINT NOT NULL COMMENT '用户ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_post_user (post_id, user_id),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='话题帖子点赞表';

CREATE TABLE IF NOT EXISTS t_topic_comment_vote (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    comment_id  BIGINT NOT NULL COMMENT '评论ID',
    user_id     BIGINT NOT NULL COMMENT '用户ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_comment_user (comment_id, user_id),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='话题评论点赞表';
