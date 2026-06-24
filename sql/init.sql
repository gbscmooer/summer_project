-- 校园二手交易平台数据库初始化脚本
-- 数据库 campus_trade 已由 Docker 环境变量自动创建

USE campus_trade;

-- ============================================================
-- 用户表（campus-user 服务使用）
-- ============================================================
CREATE TABLE IF NOT EXISTS t_user (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
    username    VARCHAR(50)  NOT NULL UNIQUE COMMENT '用户名（唯一）',
    password    VARCHAR(100) NOT NULL COMMENT '密码（BCrypt加密）',
    nickname    VARCHAR(50)  COMMENT '昵称',
    avatar      VARCHAR(255) COMMENT '头像URL',
    phone       VARCHAR(20)  COMMENT '联系方式',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- ============================================================
-- 商品表（campus-product 服务使用）
-- ============================================================
CREATE TABLE IF NOT EXISTS t_product (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '商品ID',
    title       VARCHAR(100)    NOT NULL COMMENT '标题',
    description TEXT            COMMENT '描述',
    price       DECIMAL(10, 2)  NOT NULL COMMENT '价格',
    images      VARCHAR(1000)   COMMENT '图片URL列表，逗号分隔',
    category    VARCHAR(50)     COMMENT '分类（教材/数码/生活等）',
    seller_id   BIGINT          NOT NULL COMMENT '卖家用户ID',
    status      TINYINT         DEFAULT 1 COMMENT '0-下架 1-在售 2-已售',
    stock       INT             DEFAULT 1 COMMENT '库存（二手商品一般为1）',
    view_count  INT             DEFAULT 0 COMMENT '浏览量',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_seller_id  (seller_id),
    INDEX idx_status     (status),
    INDEX idx_category   (category),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品表';

-- ============================================================
-- 订单表（campus-order 服务使用）
-- ============================================================
CREATE TABLE IF NOT EXISTS t_order (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '订单ID',
    order_no      VARCHAR(32)    NOT NULL UNIQUE COMMENT '订单号',
    product_id    BIGINT         NOT NULL COMMENT '商品ID',
    product_title VARCHAR(100)   NOT NULL COMMENT '冗余商品标题（避免跨服务查询）',
    price         DECIMAL(10, 2) NOT NULL COMMENT '成交价',
    buyer_id      BIGINT         NOT NULL COMMENT '买家用户ID',
    seller_id     BIGINT         NOT NULL COMMENT '卖家用户ID',
    status        TINYINT        DEFAULT 0 COMMENT '0-待付款 1-已付款 2-已完成 3-已取消',
    create_time   DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_buyer_id  (buyer_id),
    INDEX idx_seller_id (seller_id),
    INDEX idx_status    (status),
    INDEX idx_order_no  (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单表';

-- ============================================================
-- 通知表（campus-order 服务使用，MQ 消费者写入）
-- ============================================================
CREATE TABLE IF NOT EXISTS t_notification (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '通知ID',
    user_id     BIGINT       NOT NULL COMMENT '接收人用户ID（卖家）',
    type        VARCHAR(50)  NOT NULL DEFAULT 'ORDER_CREATED' COMMENT '通知类型',
    title       VARCHAR(100) NOT NULL COMMENT '通知标题',
    content     VARCHAR(500) NOT NULL COMMENT '通知内容',
    order_no    VARCHAR(32)  COMMENT '关联订单号',
    is_read     TINYINT      DEFAULT 0 COMMENT '0-未读 1-已读',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id   (user_id),
    INDEX idx_is_read   (is_read),
    INDEX idx_user_read (user_id, is_read)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通知表';

-- ============================================================
-- 测试数据：用户（密码明文均为 123456）
-- BCrypt("123456") = $2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBpwTTyRTceFGK
-- ============================================================
INSERT INTO t_user (username, password, nickname, avatar, phone) VALUES
('zhangsan', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBpwTTyRTceFGK', '张三', 'https://api.dicebear.com/7.x/avataaars/svg?seed=zhangsan', '13800138001'),
('lisi',     '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBpwTTyRTceFGK', '李四', 'https://api.dicebear.com/7.x/avataaars/svg?seed=lisi',     '13800138002'),
('wangwu',   '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBpwTTyRTceFGK', '王五', 'https://api.dicebear.com/7.x/avataaars/svg?seed=wangwu',   '13800138003'),
('admin',    '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBpwTTyRTceFGK', '管理员', 'https://api.dicebear.com/7.x/avataaars/svg?seed=admin', '13800138000');

-- ============================================================
-- 测试数据：商品（seller_id 关联上面的用户 id 1/2/3）
-- ============================================================
INSERT INTO t_product (title, description, price, images, category, seller_id, status, stock, view_count) VALUES
('同济版高等数学（上下册）',    '九成新，无笔迹无划线，适合大一学生',                    25.00,  'https://picsum.photos/seed/math/400/300',       '教材', 1, 1, 1, 128),
('MacBook Pro 充电线',          'MagSafe2 原装充电线，功能正常，有轻微磨损',             80.00,  'https://picsum.photos/seed/cable/400/300',      '数码', 1, 1, 1,  56),
('大学英语四级词汇书',           '新东方出版，只用了一半，整体完好',                      12.00,  'https://picsum.photos/seed/book/400/300',       '教材', 2, 1, 1,  89),
('索尼WH-1000XM3降噪耳机',      '成色9成新，附原装包装和线材，降噪效果好',              800.00,  'https://picsum.photos/seed/headphone/400/300',  '数码', 2, 1, 1, 234),
('IKEA台灯（白色LED）',          '宿舍用，LED灯泡，亮度可调，用了一学期',                35.00,  'https://picsum.photos/seed/lamp/400/300',       '生活', 3, 1, 1,  45),
('线性代数教材（浙大版）',        '考研参考用书，有部分笔记，助理解',                      20.00,  'https://picsum.photos/seed/linear/400/300',     '教材', 3, 1, 1,  67),
('自动折叠雨伞（深蓝色）',        '用过两次，结实耐用',                                  18.00,  'https://picsum.photos/seed/umbrella/400/300',   '生活', 1, 1, 1,  23),
('数据结构与算法分析（C语言版）', '严蔚敏版，有少量铅笔标注，可擦除',                     30.00,  'https://picsum.photos/seed/ds/400/300',         '教材', 2, 1, 1,  91),
('小米移动电源 10000mAh',        '充过约50次，容量正常，两个USB口',                      45.00,  'https://picsum.photos/seed/powerbank/400/300',  '数码', 3, 1, 1, 112);
