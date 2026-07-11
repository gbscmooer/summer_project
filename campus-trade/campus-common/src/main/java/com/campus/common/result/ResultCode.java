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

    // 商品业务码
    PRODUCT_NOT_FOUND(2001, "商品不存在"),
    PRODUCT_OFF_SHELF(2002, "商品已下架或已售"),
    PRODUCT_STOCK_INSUFFICIENT(2003, "库存不足"),

    // AI 业务码
    AI_NOT_CONFIGURED(4001, "AI 服务未配置，请设置 AI_API_KEY"),
    AI_RESPONSE_INVALID(4002, "AI 返回内容无法解析，请稍后重试"),
    AI_IMAGE_INVALID(4003, "商品图片不存在或格式不受支持"),
    AI_VISION_NOT_SUPPORTED(4004, "当前 AI 模型不支持图片识别，请配置支持视觉的模型"),

    // 订单业务码
    ORDER_NOT_FOUND(3001, "订单不存在"),
    ORDER_CANNOT_BUY_OWN(3002, "不能购买自己发布的商品"),
    ORDER_STATUS_INVALID(3003, "当前订单状态不允许此操作");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
