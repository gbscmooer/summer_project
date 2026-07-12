-- Unique daily like quest items to prevent unlike/re-like farming.
USE campus_trade;

CREATE TABLE IF NOT EXISTS t_daily_like_quest_item (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id     BIGINT NOT NULL COMMENT '用户ID',
    quest_date  DATE   NOT NULL COMMENT '任务日期',
    post_id     BIGINT NOT NULL COMMENT '帖子ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_date_post (user_id, quest_date, post_id),
    INDEX idx_quest_date (quest_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='每日点赞任务明细（每帖每天最多计一次）';

-- Harden point ledger idempotency for transfers that always supply ref_id.
SET @exist := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = 'campus_trade'
    AND table_name = 't_point_ledger'
    AND index_name = 'uk_user_ref_reason'
);
SET @sql := IF(@exist = 0,
  'ALTER TABLE t_point_ledger ADD UNIQUE KEY uk_user_ref_reason (user_id, ref_type, ref_id, reason)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
