-- Optional upgrade: order reviews + seller reputation aggregates.
USE campus_trade;

CREATE TABLE IF NOT EXISTS t_order_review (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '评价ID',
    order_id    BIGINT       NOT NULL COMMENT '订单ID',
    product_id  BIGINT       NOT NULL COMMENT '商品ID',
    buyer_id    BIGINT       NOT NULL COMMENT '买家用户ID',
    seller_id   BIGINT       NOT NULL COMMENT '卖家用户ID',
    rating      TINYINT      NOT NULL COMMENT '1-5',
    content     VARCHAR(500) NULL COMMENT '评价内容',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '评价时间',
    UNIQUE KEY uk_order_buyer (order_id, buyer_id),
    INDEX idx_seller (seller_id),
    INDEX idx_product (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单交易评价';

-- 卖家信誉分聚合字段（幂等：列已存在则跳过）
SET @col_avg := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_user' AND COLUMN_NAME = 'avg_rating'
);
SET @sql_avg := IF(@col_avg = 0,
    'ALTER TABLE t_user ADD COLUMN avg_rating DECIMAL(3,2) DEFAULT NULL COMMENT ''卖家平均评分 1-5'' AFTER points',
    'SELECT 1');
PREPARE stmt_avg FROM @sql_avg;
EXECUTE stmt_avg;
DEALLOCATE PREPARE stmt_avg;

SET @col_cnt := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_user' AND COLUMN_NAME = 'review_count'
);
SET @sql_cnt := IF(@col_cnt = 0,
    'ALTER TABLE t_user ADD COLUMN review_count INT NOT NULL DEFAULT 0 COMMENT ''卖家收到的评价数'' AFTER avg_rating',
    'SELECT 1');
PREPARE stmt_cnt FROM @sql_cnt;
EXECUTE stmt_cnt;
DEALLOCATE PREPARE stmt_cnt;
