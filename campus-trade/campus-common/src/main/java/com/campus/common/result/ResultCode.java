package com.campus.common.result;

import lombok.Getter;

@Getter
public enum ResultCode {
    SUCCESS(200, "success"),
    BAD_REQUEST(400, "参数错误"),
    UNAUTHORIZED(401, "未登录或Token已失效"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),
    INTERNAL_ERROR(500, "服务器内部错误"),

    // 用户业务码
    USERNAME_EXISTS(1001, "用户名已存在"),
    USERNAME_OR_PASSWORD_ERROR(1002, "用户名或密码错误"),
    MERCHANT_ALREADY(1003, "您已是商家账号"),
    MERCHANT_APPLICATION_PENDING(1004, "已有待审核的商家申请"),
    MERCHANT_APPLICATION_NOT_FOUND(1005, "商家申请不存在"),
    MERCHANT_APPLICATION_REVIEWED(1006, "该申请已处理"),
    USER_BANNED(1007, "账号已被封禁，请联系管理员"),
    CANNOT_BAN_ADMIN(1008, "不能封禁管理员账号"),
    ALREADY_CHECKED_IN(1009, "今日已签到"),
    LIKE_REWARD_NOT_READY(1010, "今日点赞未满5次，暂不可领取"),
    LIKE_REWARD_ALREADY_CLAIMED(1011, "今日点赞奖励已领取"),
    EMAIL_REQUIRED(1012, "邮箱不能为空"),
    EMAIL_INVALID(1013, "邮箱格式不正确"),
    EMAIL_EXISTS(1014, "邮箱已被注册"),
    RESET_TOKEN_INVALID(1015, "重置链接无效或已过期"),
    RESET_EMAIL_NOT_BOUND(1016, "该账号未绑定邮箱"),
    MAIL_SEND_FAILED(1017, "邮件发送失败，请稍后重试"),
    FRIEND_REQUEST_EXISTS(1018, "已发送过好友申请"),
    FRIEND_ALREADY(1019, "已经是好友"),
    FRIEND_REQUEST_NOT_FOUND(1020, "好友申请不存在"),
    FRIEND_CANNOT_SELF(1021, "不能添加自己为好友"),
    MESSAGE_LIMIT_REACHED(1022, "非好友仅可发送一条私信，请先加好友或完成交易"),
    CONVERSATION_NOT_FOUND(1023, "会话不存在"),
    MESSAGE_PEER_INVALID(1024, "无法与该用户私信"),
    FOLLOW_SELF(1025, "不能关注自己"),
    FOLLOW_ALREADY(1026, "已关注该用户"),
    FOLLOW_NOT_FOUND(1027, "未关注该用户"),
    USER_NOT_FOUND(1028, "用户不存在"),

    // 商品业务码
    PRODUCT_NOT_FOUND(2001, "商品不存在"),
    PRODUCT_OFF_SHELF(2002, "商品已下架或已售"),
    PRODUCT_STOCK_INSUFFICIENT(2003, "库存不足"),
    PRODUCT_PUBLISH_MERCHANT_ONLY(2004, "仅商家认证后可发布商品"),
    TOPIC_POST_NOT_FOUND(2005, "帖子不存在"),
    TOPIC_COMMENT_NOT_FOUND(2006, "评论不存在"),
    TOPIC_TIP_SELF(2007, "不能给自己的帖子打赏"),
    TOPIC_TIP_INVALID(2008, "打赏积分须为正整数"),
    TOPIC_TIP_DISABLED(2009, "作者未开启打赏"),

    // AI 业务码
    AI_NOT_CONFIGURED(4001, "AI 服务未配置，请设置 AI_API_KEY"),
    AI_RESPONSE_INVALID(4002, "AI 返回内容无法解析，请稍后重试"),
    AI_IMAGE_INVALID(4003, "商品图片不存在或格式不受支持"),
    AI_VISION_NOT_SUPPORTED(4004, "当前 AI 模型不支持图片识别，请配置支持视觉的模型"),

    // 订单业务码
    ORDER_NOT_FOUND(3001, "订单不存在"),
    ORDER_CANNOT_BUY_OWN(3002, "不能购买自己发布的商品"),
    ORDER_STATUS_INVALID(3003, "当前订单状态不允许此操作"),
    ORDER_PURCHASE_LIMIT(3004, "已达该商品购买上限"),
    POINTS_INSUFFICIENT(3005, "积分不足");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
