-- 商家角色与商家申请功能迁移脚本
USE campus_trade;

-- 扩展角色说明：0-个人账户 1-管理员 2-商家
ALTER TABLE t_user
    MODIFY COLUMN role TINYINT NOT NULL DEFAULT 0 COMMENT '0-个人账户 1-管理员 2-商家';

CREATE TABLE IF NOT EXISTS t_merchant_application (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '申请ID',
    user_id       BIGINT       NOT NULL COMMENT '申请人用户ID',
    shop_name     VARCHAR(100) NOT NULL COMMENT '店铺名称',
    reason        VARCHAR(500) NOT NULL COMMENT '申请说明',
    contact_phone VARCHAR(20)  NOT NULL COMMENT '联系电话',
    status        TINYINT      NOT NULL DEFAULT 0 COMMENT '0-待审核 1-已通过 2-已拒绝',
    admin_id      BIGINT       NULL COMMENT '审核管理员ID',
    admin_note    VARCHAR(255) NULL COMMENT '审核备注',
    create_time   DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商家入驻申请表';
