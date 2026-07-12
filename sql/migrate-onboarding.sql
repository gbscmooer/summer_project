-- 新手教程：用户进度字段 + 教程商品字段
-- 执行后现有用户默认视为已完成教程（老用户不展示）
-- 导入时必须指定字符集: mysql --default-character-set=utf8mb4 ...

USE campus_trade;

SET NAMES utf8mb4;

ALTER TABLE t_user
    ADD COLUMN onboarding_completed TINYINT NOT NULL DEFAULT 0 COMMENT '1-已完成/老用户，不展示新手教程' AFTER role,
    ADD COLUMN onboarding_flags VARCHAR(512) NOT NULL DEFAULT '{}' COMMENT '客户端步骤标记 JSON' AFTER onboarding_completed;

ALTER TABLE t_product
    ADD COLUMN is_tutorial TINYINT NOT NULL DEFAULT 0 COMMENT '1-新手教程专用商品' AFTER view_count,
    ADD COLUMN purchase_limit INT NULL COMMENT '每用户限购数量，NULL 表示不限' AFTER is_tutorial;

-- 已有用户视为老用户，不再展示新手教程
UPDATE t_user SET onboarding_completed = 1 WHERE onboarding_completed = 0;

-- 官方教程卖家（密码 123456，仅本地演示）
INSERT IGNORE INTO t_user (username, password, nickname, avatar, phone, role, onboarding_completed)
VALUES (
    'campus_official',
    '$2a$10$fPwyITwOcv4Ggd3cbTdX6uxOQs5X96/CuKS/4K/3cz4gZhnt7nLJi',
    '校园集市官方',
    'https://api.dicebear.com/7.x/shapes/svg?seed=campus',
    NULL,
    0,
    1
);

-- 0 元教程商品：每用户限购 2 件
DELETE FROM t_product
WHERE is_tutorial = 1
  AND seller_id = (SELECT id FROM (SELECT id FROM t_user WHERE username = 'campus_official') AS u);

INSERT INTO t_product (title, description, price, images, category, seller_id, status, stock, view_count, is_tutorial, purchase_limit)
VALUES (
    '【新手体验】校园集市入门礼包',
    '专为新用户准备的 0 元体验商品，完成新手教程中的购买、付款与确认收货流程。每位用户限购 2 件。',
    0.00,
    'https://picsum.photos/seed/tutorial/400/300',
    '生活',
    (SELECT id FROM t_user WHERE username = 'campus_official'),
    1,
    9999,
    0,
    1,
    2
);
