-- 校园集市数据库初始化脚本
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
    bio         VARCHAR(120) NOT NULL DEFAULT '' COMMENT '个性签名',
    avatar      VARCHAR(255) COMMENT '头像URL',
    cover_image VARCHAR(500) NOT NULL DEFAULT '' COMMENT '主页封面图 URL',
    ip_location VARCHAR(50)  NOT NULL DEFAULT '' COMMENT '展示用 IP 属地',
    phone       VARCHAR(20)  COMMENT '联系方式',
    email       VARCHAR(120) NULL COMMENT '绑定邮箱',
    email_verified TINYINT NOT NULL DEFAULT 0 COMMENT '0-未验证 1-已验证',
    role        TINYINT      NOT NULL DEFAULT 0 COMMENT '0-个人账户 1-管理员 2-商家',
    status      TINYINT      NOT NULL DEFAULT 0 COMMENT '0-正常 1-已封禁',
    ban_reason  VARCHAR(500) COMMENT '封禁原因',
    ban_until   DATETIME     COMMENT '封禁截止时间，NULL 表示永久',
    banned_by   BIGINT       COMMENT '执行封禁的管理员ID',
    banned_at   DATETIME     COMMENT '封禁时间',
    onboarding_completed TINYINT NOT NULL DEFAULT 0 COMMENT '1-已完成新手教程',
    onboarding_flags     VARCHAR(512) NOT NULL DEFAULT '{}' COMMENT '新手教程步骤标记 JSON',
    points      INT          NOT NULL DEFAULT 100 COMMENT '积分余额（新用户赠送100）',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

CREATE UNIQUE INDEX uk_user_email ON t_user (email);

CREATE TABLE IF NOT EXISTS t_password_reset_token (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    token_hash  CHAR(64)     NOT NULL COMMENT 'SHA-256 hex of raw token',
    expires_at  DATETIME     NOT NULL,
    used_at     DATETIME     NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_token_hash (token_hash),
    INDEX idx_user_id (user_id),
    INDEX idx_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='密码重置令牌';


-- ============================================================
-- 商品表（campus-product 服务使用）
-- ============================================================
CREATE TABLE IF NOT EXISTS t_product (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '商品ID',
    title       VARCHAR(100)    NOT NULL COMMENT '标题',
    description TEXT            COMMENT '描述',
    price       DECIMAL(10, 2)  NOT NULL COMMENT '所需积分',
    images      VARCHAR(1000)   COMMENT '图片URL列表，逗号分隔',
    category    VARCHAR(50)     COMMENT '分类（教材/数码/生活等）',
    seller_id   BIGINT          NOT NULL COMMENT '卖家用户ID',
    status      TINYINT         DEFAULT 1 COMMENT '0-下架 1-在售 2-已售',
    stock       INT             DEFAULT 1 COMMENT '库存（二手商品一般为1）',
    view_count  INT             DEFAULT 0 COMMENT '浏览量',
    is_tutorial TINYINT         NOT NULL DEFAULT 0 COMMENT '1-新手教程专用商品',
    purchase_limit INT          NULL COMMENT '每用户限购数量，NULL 表示不限',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_seller_id  (seller_id),
    INDEX idx_status     (status),
    INDEX idx_category   (category),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品表';

-- ============================================================
-- 商品留言表（campus-product 服务使用）
-- ============================================================
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

-- ============================================================
-- 话题帖子（campus-product 服务使用）
-- ============================================================
CREATE TABLE IF NOT EXISTS t_topic_post (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '帖子ID',
    user_id       BIGINT       NOT NULL COMMENT '作者用户ID',
    title         VARCHAR(100) NOT NULL COMMENT '标题',
    content       TEXT         NOT NULL COMMENT '正文',
    upvote_count  INT          NOT NULL DEFAULT 0 COMMENT '点赞数',
    comment_count INT          NOT NULL DEFAULT 0 COMMENT '评论数',
    tip_total     INT          NOT NULL DEFAULT 0 COMMENT '累计打赏积分',
    tip_enabled   TINYINT      NOT NULL DEFAULT 0 COMMENT '0-关闭打赏 1-开启打赏',
    create_time   DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
    update_time   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='话题帖子表';

CREATE TABLE IF NOT EXISTS t_topic_post_product (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '关联ID',
    post_id     BIGINT NOT NULL COMMENT '帖子ID',
    product_id  BIGINT NOT NULL COMMENT '商品ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_post_product (post_id, product_id),
    INDEX idx_post_id (post_id),
    INDEX idx_product_id (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='帖子附带商品关联表';

CREATE TABLE IF NOT EXISTS t_topic_comment (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '评论ID',
    post_id      BIGINT        NOT NULL COMMENT '帖子ID',
    user_id      BIGINT        NOT NULL COMMENT '评论用户ID',
    parent_id    BIGINT        NULL COMMENT '父评论ID，空为顶层',
    content      VARCHAR(1000) NOT NULL COMMENT '评论内容',
    image_url    VARCHAR(255)  NULL COMMENT '可选配图 URL',
    upvote_count INT           NOT NULL DEFAULT 0 COMMENT '点赞数',
    create_time  DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '评论时间',
    INDEX idx_post_id (post_id),
    INDEX idx_parent_id (parent_id),
    INDEX idx_user_id (user_id),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='话题评论表';

CREATE TABLE IF NOT EXISTS t_topic_post_vote (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    post_id     BIGINT NOT NULL COMMENT '帖子ID',
    user_id     BIGINT NOT NULL COMMENT '用户ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_post_user (post_id, user_id),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='话题帖子点赞表';

CREATE TABLE IF NOT EXISTS t_topic_comment_vote (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    comment_id  BIGINT NOT NULL COMMENT '评论ID',
    user_id     BIGINT NOT NULL COMMENT '用户ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_comment_user (comment_id, user_id),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='话题评论点赞表';

CREATE TABLE IF NOT EXISTS t_topic_tip_receipt (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    post_id     BIGINT       NOT NULL COMMENT '帖子ID',
    tipper_id   BIGINT       NOT NULL COMMENT '打赏人ID',
    amount      INT          NOT NULL COMMENT '打赏积分',
    request_id  VARCHAR(64)  NOT NULL COMMENT '客户端幂等键',
    status      TINYINT      NOT NULL DEFAULT 0 COMMENT '0-处理中 1-已完成',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_request_id (request_id),
    INDEX idx_post_id (post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='话题打赏收据';

-- ============================================================
-- 订单表（campus-order 服务使用）
-- ============================================================
CREATE TABLE IF NOT EXISTS t_order (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '订单ID',
    order_no      VARCHAR(32)    NOT NULL UNIQUE COMMENT '订单号',
    request_id    VARCHAR(32)    NULL UNIQUE COMMENT '秒杀请求幂等标识，普通订单为空',
    product_id    BIGINT         NOT NULL COMMENT '商品ID',
    product_title VARCHAR(100)   NOT NULL COMMENT '冗余商品标题（避免跨服务查询）',
    price         DECIMAL(10, 2) NOT NULL COMMENT '成交积分',
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

-- ============================================================
-- 商家入驻申请表（campus-user 服务使用）
-- ============================================================
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

-- ============================================================
-- 积分流水 / 签到 / 每日点赞任务（campus-user 服务使用）
-- ============================================================
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
    INDEX idx_create_time (create_time),
    UNIQUE KEY uk_user_ref_reason (user_id, ref_type, ref_id, reason)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='积分流水表';

CREATE TABLE IF NOT EXISTS t_daily_checkin (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '记录ID',
    user_id         BIGINT NOT NULL COMMENT '用户ID',
    checkin_date    DATE   NOT NULL COMMENT '签到日期',
    points_awarded  INT    NOT NULL DEFAULT 10 COMMENT '发放积分',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_date (user_id, checkin_date),
    INDEX idx_checkin_date (checkin_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='每日签到表';

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

CREATE TABLE IF NOT EXISTS t_daily_like_quest_item (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id     BIGINT NOT NULL COMMENT '用户ID',
    quest_date  DATE   NOT NULL COMMENT '任务日期',
    post_id     BIGINT NOT NULL COMMENT '帖子ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_date_post (user_id, quest_date, post_id),
    INDEX idx_quest_date (quest_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='每日点赞任务明细（每帖每天最多计一次）';

CREATE TABLE IF NOT EXISTS t_friend_request (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    from_user_id BIGINT NOT NULL COMMENT '申请人',
    to_user_id   BIGINT NOT NULL COMMENT '被申请人',
    status      TINYINT NOT NULL DEFAULT 0 COMMENT '0-待处理 1-已同意 2-已拒绝',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_from_to (from_user_id, to_user_id),
    INDEX idx_to_status (to_user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='好友申请';

CREATE TABLE IF NOT EXISTS t_friendship (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_low_id  BIGINT NOT NULL COMMENT '较小用户ID',
    user_high_id BIGINT NOT NULL COMMENT '较大用户ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_pair (user_low_id, user_high_id),
    INDEX idx_high (user_high_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='双向好友关系';

CREATE TABLE IF NOT EXISTS t_conversation (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_low_id  BIGINT NOT NULL,
    user_high_id BIGINT NOT NULL,
    last_msg_preview VARCHAR(200) DEFAULT NULL,
    last_message_at DATETIME DEFAULT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_pair (user_low_id, user_high_id),
    INDEX idx_last_msg (last_message_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='私信会话';

CREATE TABLE IF NOT EXISTS t_message (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    conversation_id BIGINT NOT NULL,
    sender_id   BIGINT NOT NULL,
    content     VARCHAR(2000) NOT NULL,
    is_read     TINYINT NOT NULL DEFAULT 0 COMMENT '对接收方是否已读',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_conv_time (conversation_id, create_time),
    INDEX idx_sender (sender_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='私信消息';

CREATE TABLE IF NOT EXISTS t_user_follow (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    follower_id BIGINT NOT NULL COMMENT '关注者',
    followee_id BIGINT NOT NULL COMMENT '被关注者',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_pair (follower_id, followee_id),
    INDEX idx_followee (followee_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户关注';
