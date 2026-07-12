-- Optional upgrade: product favorites / wishlist.
USE campus_trade;

CREATE TABLE IF NOT EXISTS t_product_favorite (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '收藏ID',
    user_id     BIGINT NOT NULL COMMENT '用户ID',
    product_id  BIGINT NOT NULL COMMENT '商品ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
    UNIQUE KEY uk_user_product (user_id, product_id),
    INDEX idx_user_create (user_id, create_time),
    INDEX idx_product_id (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品收藏表';
