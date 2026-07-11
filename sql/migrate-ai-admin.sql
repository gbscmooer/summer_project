-- Optional upgrade for existing campus_trade volumes created before AI admin support.
-- Safe to re-run: skips columns/tables that already exist.

USE campus_trade;

SET @has_role := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_user' AND COLUMN_NAME = 'role'
);
SET @sql := IF(@has_role = 0,
  'ALTER TABLE t_user ADD COLUMN role TINYINT NOT NULL DEFAULT 0 COMMENT ''0-普通用户 1-管理员'' AFTER phone',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 管理员必须通过受控的离线流程显式授权；迁移脚本不得按可抢注用户名自动提权。

SET @has_request_id := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_order' AND COLUMN_NAME = 'request_id'
);
SET @sql := IF(@has_request_id = 0,
  'ALTER TABLE t_order ADD COLUMN request_id VARCHAR(32) NULL UNIQUE COMMENT ''秒杀请求幂等标识，普通订单为空'' AFTER order_no',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS t_product_image (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '图片记录ID',
    filename    VARCHAR(64)  NOT NULL UNIQUE COMMENT '存储文件名',
    uploader_id BIGINT       NOT NULL COMMENT '上传者用户ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_uploader_id (uploader_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品实拍图元数据';

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

ALTER TABLE t_ai_config MODIFY COLUMN api_key VARCHAR(1024) COMMENT 'AES-GCM 加密后的 API Key 密文';

SET @has_api_key_base_url := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_ai_config' AND COLUMN_NAME = 'api_key_base_url'
);
SET @sql := IF(@has_api_key_base_url = 0,
  'ALTER TABLE t_ai_config ADD COLUMN api_key_base_url VARCHAR(255) NULL COMMENT ''API Key 绑定的规范化 API 根地址'' AFTER api_key',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

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

-- Existing non-cancelled orders were already deducted before this ledger existed.
INSERT IGNORE INTO t_stock_deduction_log (product_id, order_no)
SELECT product_id, order_no FROM t_order WHERE status <> 3;

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

-- 自动化测试残留商品：下架，避免污染市场与 AI 搜索
UPDATE t_product SET status = 0 WHERE title LIKE 'Strict delivery%';
