-- 评价信誉增量幂等日志 + 订单评价同步标记
CREATE TABLE IF NOT EXISTS t_rating_apply_log (
    review_id   BIGINT PRIMARY KEY COMMENT '订单评价ID',
    seller_id   BIGINT  NOT NULL COMMENT '卖家用户ID',
    rating      TINYINT NOT NULL COMMENT '1-5',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '落账时间',
    INDEX idx_seller (seller_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='卖家信誉增量幂等日志';

SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 't_order_review'
      AND COLUMN_NAME = 'rating_applied'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE t_order_review ADD COLUMN rating_applied TINYINT NOT NULL DEFAULT 0 COMMENT ''0-未同步信誉 1-已同步'' AFTER content',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
