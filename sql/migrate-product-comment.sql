-- Optional upgrade: product comments / messages on listing detail pages.
USE campus_trade;

CREATE TABLE IF NOT EXISTS t_product_comment (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '留言ID',
    product_id  BIGINT       NOT NULL COMMENT '商品ID',
    user_id     BIGINT       NOT NULL COMMENT '留言用户ID',
    content     VARCHAR(500) NOT NULL COMMENT '留言内容',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '留言时间',
    INDEX idx_product_id (product_id),
    INDEX idx_user_id (user_id),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品留言表';
