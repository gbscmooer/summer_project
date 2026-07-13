-- Optional upgrade: product sale type for normal/seckill purchase routing.
-- Safe to re-run: skips the column when it already exists.

USE campus_trade;

SET @has_sale_type := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_product' AND COLUMN_NAME = 'sale_type'
);
SET @sql_sale_type := IF(@has_sale_type = 0,
  'ALTER TABLE t_product ADD COLUMN sale_type TINYINT NOT NULL DEFAULT 0 COMMENT ''0-普通购买 1-秒杀'' AFTER purchase_limit',
  'SELECT 1');
PREPARE stmt_sale_type FROM @sql_sale_type;
EXECUTE stmt_sale_type;
DEALLOCATE PREPARE stmt_sale_type;

-- 压测锚点商品（高等数学 / JMeter 秒杀）标记为秒杀，便于已有库升级后前端自动分流
UPDATE t_product
SET sale_type = 1
WHERE sale_type = 0
  AND (
    title = '同济版高等数学（上下册）'
    OR description LIKE '%JMeter 秒杀%'
  );
