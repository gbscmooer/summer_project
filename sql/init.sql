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
    role        TINYINT      NOT NULL DEFAULT 0 COMMENT '0-普通用户 1-管理员',
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
    request_id    VARCHAR(32)    NULL UNIQUE COMMENT '秒杀请求幂等标识，普通订单为空',
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
-- 商品实拍图元数据（campus-product：归属校验）
-- ============================================================
CREATE TABLE IF NOT EXISTS t_product_image (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '图片记录ID',
    filename    VARCHAR(64)  NOT NULL UNIQUE COMMENT '存储文件名',
    uploader_id BIGINT       NOT NULL COMMENT '上传者用户ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_uploader_id (uploader_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品实拍图元数据';

-- ============================================================
-- AI 运行时配置（campus-product：管理员可热更新，单行）
-- ============================================================
CREATE TABLE IF NOT EXISTS t_ai_config (
    id               BIGINT PRIMARY KEY COMMENT '固定为1',
    enabled          TINYINT      NOT NULL DEFAULT 0 COMMENT '1-使用本表覆盖环境变量',
    base_url         VARCHAR(255) COMMENT 'OpenAI-compatible API 根地址',
    api_key          VARCHAR(1024) COMMENT 'AES-GCM 加密后的 API Key 密文',
    api_key_base_url VARCHAR(255) COMMENT 'API Key 绑定的规范化 API 根地址',
    model            VARCHAR(100) COMMENT '模型名',
    timeout_seconds  INT          COMMENT '请求超时秒数',
    supports_vision  TINYINT      COMMENT '1-支持图片识别',
    updated_by       BIGINT       COMMENT '最后修改人',
    update_time      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI运行时配置';

CREATE TABLE IF NOT EXISTS t_stock_restore_log (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    product_id  BIGINT      NOT NULL,
    order_no    VARCHAR(32) NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_product_order (product_id, order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存恢复持久幂等流水';

CREATE TABLE IF NOT EXISTS t_stock_deduction_log (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    product_id  BIGINT      NOT NULL,
    order_no    VARCHAR(32) NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_deduction_product_order (product_id, order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存扣减持久幂等流水';

CREATE TABLE IF NOT EXISTS t_stock_compensation_task (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    product_id      BIGINT       NOT NULL,
    order_no        VARCHAR(32)  NOT NULL,
    status          TINYINT      NOT NULL DEFAULT 0 COMMENT '0-待补偿 1-已完成',
    attempts        INT          NOT NULL DEFAULT 0,
    next_retry_time DATETIME     NOT NULL,
    last_error      VARCHAR(255),
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_compensation_order_no (order_no),
    INDEX idx_compensation_due (status, next_retry_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单库存补偿任务';
