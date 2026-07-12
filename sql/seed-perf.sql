-- Performance / load-test seed data (local & staging only — never run in production).
-- Password for all seeded accounts: 123456
-- BCrypt("123456") via campus-user BCryptPasswordEncoder
-- Admin: admin / 123456 (role=1)

USE campus_trade;

SET @pwd = '$2a$10$fPwyITwOcv4Ggd3cbTdX6uxOQs5X96/CuKS/4K/3cz4gZhnt7nLJi';

-- ---------------------------------------------------------------------------
-- Idempotent cleanup: reset perf tables so ids start at 1 (JMeter uses productId=1)
-- ---------------------------------------------------------------------------
SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE t_notification;
TRUNCATE TABLE t_stock_deduction_log;
TRUNCATE TABLE t_stock_restore_log;
TRUNCATE TABLE t_stock_compensation_task;
TRUNCATE TABLE t_order;
TRUNCATE TABLE t_product;
TRUNCATE TABLE t_user;
SET FOREIGN_KEY_CHECKS = 1;

-- ---------------------------------------------------------------------------
-- Users: 1 admin + 3 demo + 200 load-test accounts
-- ---------------------------------------------------------------------------
INSERT INTO t_user (username, password, nickname, avatar, phone, role) VALUES
('admin',    @pwd, '系统管理员', 'https://api.dicebear.com/7.x/avataaars/svg?seed=admin',    '13800000000', 1),
('zhangsan', @pwd, '张三',       'https://api.dicebear.com/7.x/avataaars/svg?seed=zhangsan', '13800138001', 0),
('lisi',     @pwd, '李四',       'https://api.dicebear.com/7.x/avataaars/svg?seed=lisi',     '13800138002', 0),
('wangwu',   @pwd, '王五',       'https://api.dicebear.com/7.x/avataaars/svg?seed=wangwu',   '13800138003', 0);

INSERT INTO t_user (username, password, nickname, avatar, phone, role)
WITH RECURSIVE seq AS (
    SELECT 1 AS i
    UNION ALL
    SELECT i + 1 FROM seq WHERE i < 200
)
SELECT
    CONCAT('perfuser', LPAD(i, 3, '0')),
    @pwd,
    CONCAT('压测用户', i),
    CONCAT('https://api.dicebear.com/7.x/avataaars/svg?seed=perf', i),
    CONCAT('139', LPAD(10000000 + i, 8, '0')),
    0
FROM seq;

-- ---------------------------------------------------------------------------
-- Products: 1000 items (id=1 reserved for JMeter seckill: 高等数学 + stock=100)
-- ---------------------------------------------------------------------------
INSERT INTO t_product (title, description, price, images, category, seller_id, status, stock, view_count)
SELECT
    '同济版高等数学（上下册）',
    'perf-seed: JMeter 秒杀压测锚点商品，含「数学」关键词便于搜索压测',
    25.00,
    'https://picsum.photos/seed/math-anchor/400/300',
    '教材',
    (SELECT id FROM t_user WHERE username = 'zhangsan' LIMIT 1),
    1,
    100,
    256;

INSERT INTO t_product (title, description, price, images, category, seller_id, status, stock, view_count)
WITH RECURSIVE seq AS (
    SELECT 1 AS i
    UNION ALL
    SELECT i + 1 FROM seq WHERE i < 999
),
user_bounds AS (
    SELECT MIN(id) AS min_id, COUNT(*) AS cnt FROM t_user
)
SELECT
    ELT(1 + (i % 15),
        CONCAT('高等数学教材（第', i, '册）'),
        CONCAT('线性代数习题集', i),
        CONCAT('概率论与数理统计', i),
        CONCAT('大学物理课本', i),
        CONCAT('英语四级词汇书', i),
        CONCAT('数据结构与算法分析', i),
        CONCAT('计算机网络原理', i),
        CONCAT('iPhone 保护壳', i),
        CONCAT('蓝牙耳机', i),
        CONCAT('机械键盘', i),
        CONCAT('USB-C 数据线', i),
        CONCAT('宿舍台灯 LED', i),
        CONCAT('收纳箱大号', i),
        CONCAT('运动鞋九成新', i),
        CONCAT('保温杯 500ml', i)
    ),
    CONCAT('perf-seed: 校园二手压测数据集第 ', i, ' 条'),
    ROUND(8.00 + (i % 120) + ((i % 9) * 0.25), 2),
    CONCAT('https://picsum.photos/seed/perf', i, '/400/300'),
    ELT(1 + (i % 4), '教材', '数码', '生活', '运动'),
    ub.min_id + (i % ub.cnt),
    1,
    1 + (i % 8),
    20 + (i * 17 % 480)
FROM seq
CROSS JOIN user_bounds ub;
