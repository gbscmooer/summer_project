-- 积分体系与活动奖励迁移脚本
USE campus_trade;

-- 用户积分余额（新用户默认 100）。若列已存在会报错，可忽略。
ALTER TABLE t_user
    ADD COLUMN points INT NOT NULL DEFAULT 100 COMMENT '积分余额' AFTER onboarding_flags;

-- 商品/订单价格语义改为积分（字段类型不变）
ALTER TABLE t_product
    MODIFY COLUMN price DECIMAL(10, 2) NOT NULL COMMENT '所需积分';

ALTER TABLE t_order
    MODIFY COLUMN price DECIMAL(10, 2) NOT NULL COMMENT '成交积分';

-- 积分流水
CREATE TABLE IF NOT EXISTS t_point_ledger (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '流水ID',
    user_id       BIGINT       NOT NULL COMMENT '用户ID',
    delta         INT          NOT NULL COMMENT '变动值（正为入账，负为出账）',
    balance_after INT          NOT NULL COMMENT '变动后余额',
    reason        VARCHAR(64)  NOT NULL COMMENT '变动原因',
    ref_type      VARCHAR(32)  NULL COMMENT '关联类型：ORDER/CHECKIN/LIKE_QUEST 等',
    ref_id        VARCHAR(64)  NULL COMMENT '关联业务ID',
    create_time   DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_user_id (user_id),
    INDEX idx_ref (ref_type, ref_id),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='积分流水表';

-- 每日签到
CREATE TABLE IF NOT EXISTS t_daily_checkin (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '记录ID',
    user_id         BIGINT NOT NULL COMMENT '用户ID',
    checkin_date    DATE   NOT NULL COMMENT '签到日期',
    points_awarded  INT    NOT NULL DEFAULT 10 COMMENT '发放积分',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_date (user_id, checkin_date),
    INDEX idx_checkin_date (checkin_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='每日签到表';

-- 每日点赞任务进度
CREATE TABLE IF NOT EXISTS t_daily_like_quest (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '记录ID',
    user_id     BIGINT  NOT NULL COMMENT '用户ID',
    quest_date  DATE    NOT NULL COMMENT '任务日期',
    like_count  INT     NOT NULL DEFAULT 0 COMMENT '当日有效点赞次数',
    rewarded    TINYINT NOT NULL DEFAULT 0 COMMENT '0-未领奖 1-已领奖',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_quest_date (user_id, quest_date),
    INDEX idx_quest_date (quest_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='每日点赞任务表';
