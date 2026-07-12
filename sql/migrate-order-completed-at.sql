-- Optional upgrade: order completed_at + unpaid timeout scan index.
-- Safe to re-run: skips column/index that already exist.

USE campus_trade;

SET @has_completed_at := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_order' AND COLUMN_NAME = 'completed_at'
);
SET @sql_completed := IF(@has_completed_at = 0,
  'ALTER TABLE t_order ADD COLUMN completed_at DATETIME NULL COMMENT ''确认收货完成时间（评价窗口起点）'' AFTER status',
  'SELECT 1');
PREPARE stmt_completed FROM @sql_completed;
EXECUTE stmt_completed;
DEALLOCATE PREPARE stmt_completed;

-- 历史已完成订单：用 update_time 回填，避免评价窗口立刻失效
UPDATE t_order
SET completed_at = COALESCE(update_time, create_time)
WHERE status = 2 AND completed_at IS NULL;

SET @has_idx := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_order' AND INDEX_NAME = 'idx_status_create'
);
SET @sql_idx := IF(@has_idx = 0,
  'ALTER TABLE t_order ADD INDEX idx_status_create (status, create_time)',
  'SELECT 1');
PREPARE stmt_idx FROM @sql_idx;
EXECUTE stmt_idx;
DEALLOCATE PREPARE stmt_idx;
