-- Optional upgrade: user ban status for admin moderation.
USE campus_trade;

SET @has_status := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_user' AND COLUMN_NAME = 'status'
);
SET @sql := IF(@has_status = 0,
  'ALTER TABLE t_user ADD COLUMN status TINYINT NOT NULL DEFAULT 0 COMMENT ''0-正常 1-已封禁'' AFTER role',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
