package com.campus.order.util;

import java.util.UUID;

/**
 * 订单号生成器：使用 128 位随机 UUID，移除连字符后固定 32 位。
 * 数据库唯一约束仍作为最终一致性兜底。
 */
public final class OrderNoGenerator {

    private OrderNoGenerator() {
    }

    public static String generate() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
