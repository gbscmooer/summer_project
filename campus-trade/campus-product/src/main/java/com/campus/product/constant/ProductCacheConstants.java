package com.campus.product.constant;

import java.time.Duration;

/**
 * 商品缓存相关常量：key 前缀与 TTL 集中管理，避免魔法值散落。
 */
public final class ProductCacheConstants {

    private ProductCacheConstants() {
    }

    /** 商品详情缓存 key 前缀：product:detail:{id} */
    public static final String DETAIL_KEY_PREFIX = "product:detail:";

    /** 缓存重建互斥锁 key 前缀：product:lock:{id} */
    public static final String LOCK_KEY_PREFIX = "product:lock:";

    /** 详情缓存基础 TTL（30 分钟） */
    public static final Duration DETAIL_TTL_BASE = Duration.ofMinutes(30);

    /** 详情缓存随机 TTL 上限（0~5 分钟），用于缓存雪崩错峰 */
    public static final Duration DETAIL_TTL_RANDOM_MAX = Duration.ofMinutes(5);

    /** 空值缓存 TTL（60 秒），用于缓存穿透防护 */
    public static final Duration NULL_TTL = Duration.ofSeconds(60);

    /** 重建锁 TTL（10 秒），防止持锁线程崩溃导致死锁 */
    public static final Duration LOCK_TTL = Duration.ofSeconds(10);

    /** 未抢到锁时的重试间隔（毫秒） */
    public static final long LOCK_RETRY_INTERVAL_MS = 50L;

    /** 未抢到锁时的最大重试次数 */
    public static final int LOCK_MAX_RETRY = 5;

    /** 缓存预热条数：浏览量 Top N */
    public static final int WARM_UP_TOP_N = 10;

    public static String detailKey(Long id) {
        return DETAIL_KEY_PREFIX + id;
    }

    public static String lockKey(Long id) {
        return LOCK_KEY_PREFIX + id;
    }
}
