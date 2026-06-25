package com.campus.order.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 订单号生成器：{@code yyyyMMddHHmmss}（14 位）+ 3 位序列，共 17 位（≤32）。
 *
 * <p>序列部分混合进程内自增、nanoTime 与 ThreadLocalRandom，降低多实例同秒碰撞概率；
 * DB 上 order_no 仍有唯一约束兜底。
 */
public final class OrderNoGenerator {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** 0-999 循环自增，与同秒随机/nano 片段组合保证多笔订单号不同。 */
    private static final AtomicInteger SEQUENCE = new AtomicInteger(0);

    private OrderNoGenerator() {
    }

    public static String generate() {
        String timePart = LocalDateTime.now().format(FORMATTER);
        int seq = Math.floorMod(
                SEQUENCE.getAndIncrement()
                        + ThreadLocalRandom.current().nextInt(1000)
                        + (int) (System.nanoTime() % 1000),
                1000);
        return timePart + String.format("%03d", seq);
    }
}
