-- 修复教程商品与官方卖家因未按 utf8mb4 导入导致的乱码
-- 用法: mysql --default-character-set=utf8mb4 ... < sql/migrate-fix-tutorial-charset.sql

USE campus_trade;

UPDATE t_user
SET nickname = '校园集市官方'
WHERE username = 'campus_official';

UPDATE t_product
SET title = '【新手体验】校园集市入门礼包',
    description = '专为新用户准备的 0 元体验商品，完成新手教程中的购买、付款与确认收货流程。每位用户限购 2 件。',
    category = '生活'
WHERE is_tutorial = 1;
