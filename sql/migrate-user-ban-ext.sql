-- Optional upgrade: ban reason, duration, audit fields for admin moderation.
USE campus_trade;

SET @has_ban_reason := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_user' AND COLUMN_NAME = 'ban_reason'
);
SET @sql := IF(@has_ban_reason = 0,
  'ALTER TABLE t_user
     ADD COLUMN ban_reason VARCHAR(500) NULL COMMENT ''封禁原因'' AFTER status,
     ADD COLUMN ban_until DATETIME NULL COMMENT ''封禁截止时间，NULL 表示永久'' AFTER ban_reason,
     ADD COLUMN banned_by BIGINT NULL COMMENT ''执行封禁的管理员ID'' AFTER ban_until,
     ADD COLUMN banned_at DATETIME NULL COMMENT ''封禁时间'' AFTER banned_by',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
