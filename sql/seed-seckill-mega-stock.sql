-- 超大库存秒杀压测商品：stock = 1000 / 1万 / 10万 / 100万
-- 可重复执行：按标题幂等 upsert（先删同名再插）
-- Usage:
--   docker exec -i campus-mysql mysql -ucampus -p"$MYSQL_PASSWORD" campus_trade < sql/seed-seckill-mega-stock.sql

USE campus_trade;

SET @seller_id := (SELECT id FROM t_user WHERE username = 'zhangsan' LIMIT 1);
SET @seller_id := IFNULL(@seller_id, (SELECT id FROM t_user ORDER BY id ASC LIMIT 1));

DELETE FROM t_product
WHERE title IN (
  '【压测】秒杀库存1000',
  '【压测】秒杀库存1万',
  '【压测】秒杀库存10万',
  '【压测】秒杀库存100万'
);

INSERT INTO t_product
  (title, description, price, images, category, seller_id, status, stock, view_count, sale_type, purchase_limit)
VALUES
  ('【压测】秒杀库存1000',
   'perf-seed: mega stock seckill anchor stock=1000',
   1.00, 'https://picsum.photos/seed/seckill-1k/400/300', '教材', @seller_id, 1, 1000, 0, 1, NULL),
  ('【压测】秒杀库存1万',
   'perf-seed: mega stock seckill anchor stock=10000',
   1.00, 'https://picsum.photos/seed/seckill-10k/400/300', '教材', @seller_id, 1, 10000, 0, 1, NULL),
  ('【压测】秒杀库存10万',
   'perf-seed: mega stock seckill anchor stock=100000',
   1.00, 'https://picsum.photos/seed/seckill-100k/400/300', '教材', @seller_id, 1, 100000, 0, 1, NULL),
  ('【压测】秒杀库存100万',
   'perf-seed: mega stock seckill anchor stock=1000000',
   1.00, 'https://picsum.photos/seed/seckill-1m/400/300', '教材', @seller_id, 1, 1000000, 0, 1, NULL);

SELECT id, title, stock, sale_type, status, seller_id
FROM t_product
WHERE title LIKE '【压测】秒杀库存%'
ORDER BY stock ASC;
