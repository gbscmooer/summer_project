package com.campus.order.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 订单号生成器：{@code yyyyMMddHHmmss}（14 位）+ 3 位自增序列，共 17 位（≤32）。
 *
 * <p>同一秒内用进程内自增序列保证不重复（单实例足够；多实例下叠加随机更稳，
 * 这里业务量小，DB 上 order_no 仍有唯一约束兜底）。
 */
public final class OrderNoGenerator {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** 0-999 循环自增，保证同一秒内多笔订单号不同。 */
    private static final AtomicInteger SEQUENCE = new AtomicInteger(0);

    private OrderNoGenerator() {
    }

    public static String generate() {
        String timePart = LocalDateTime.now().format(FORMATTER);
        int seq = Math.floorMod(SEQUENCE.getAndIncrement(), 1000);
        return timePart + String.format("%03d", seq);
    }
}
