package com.campus.product.ai.service;

import com.campus.common.exception.BizException;
import com.campus.product.ai.dto.AiUsageStatsView;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Supplier;

/** Enforces a durable per-user daily AI request budget before expensive work begins. */
@Service
public class AiUsageGuard {

    private final StringRedisTemplate redisTemplate;
    private final long dailyLimit;
    private final long globalConcurrency;
    private static final String GLOBAL_KEY = "ai:global:inflight";
    private static final String DAILY_TOTAL_PREFIX = "ai:daily:total:";
    private static final String DAILY_USERS_PREFIX = "ai:daily:users:";
    private static final DefaultRedisScript<Long> ACQUIRE_SCRIPT = new DefaultRedisScript<>("""
            local current = tonumber(redis.call('get', KEYS[1]) or '0')
            if current >= tonumber(ARGV[1]) then return 0 end
            current = redis.call('incr', KEYS[1])
            redis.call('expire', KEYS[1], tonumber(ARGV[2]))
            return current
            """, Long.class);
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>("""
            local current = tonumber(redis.call('get', KEYS[1]) or '0')
            if current <= 1 then redis.call('del', KEYS[1]); return 0 end
            return redis.call('decr', KEYS[1])
            """, Long.class);

    public AiUsageGuard(StringRedisTemplate redisTemplate,
                        @Value("${campus.ai.daily-user-limit:100}") long dailyLimit,
                        @Value("${campus.ai.global-concurrency:8}") long globalConcurrency) {
        this.redisTemplate = redisTemplate;
        this.dailyLimit = Math.max(1, dailyLimit);
        this.globalConcurrency = Math.max(1, globalConcurrency);
    }

    public <T> T execute(Long userId, Supplier<T> action) {
        requireQuota(userId);
        boolean acquired = false;
        try {
            Long slot = redisTemplate.execute(ACQUIRE_SCRIPT, List.of(GLOBAL_KEY),
                    String.valueOf(globalConcurrency), "120");
            if (slot == null || slot == 0L) {
                throw new BizException(429, "AI 服务繁忙，请稍后重试");
            }
            acquired = true;
            return action.get();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(500, "AI 配额服务暂不可用");
        } finally {
            if (acquired) {
                try {
                    redisTemplate.execute(RELEASE_SCRIPT, List.of(GLOBAL_KEY));
                } catch (Exception ignored) {
                    // The 120-second lease expires even if Redis is transiently unavailable here.
                }
            }
        }
    }

    public void requireQuota(Long userId) {
        if (userId == null) {
            throw new BizException(401, "未登录或Token已失效");
        }
        String date = LocalDate.now(ZoneOffset.UTC).toString();
        String key = "ai:daily:" + date + ":" + userId;
        try {
            Long used = redisTemplate.opsForValue().increment(key);
            if (used != null && used == 1L) {
                redisTemplate.expire(key, Duration.ofDays(2));
            }
            String usersSetKey = DAILY_USERS_PREFIX + date;
            redisTemplate.opsForSet().add(usersSetKey, String.valueOf(userId));
            redisTemplate.expire(usersSetKey, Duration.ofDays(2));
            String totalKey = DAILY_TOTAL_PREFIX + date;
            Long total = redisTemplate.opsForValue().increment(totalKey);
            if (total != null && total == 1L) {
                redisTemplate.expire(totalKey, Duration.ofDays(2));
            }
            if (used == null || used > dailyLimit) {
                throw new BizException(429, "今日 AI 使用额度已用完");
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            // Fail closed: losing Redis must not turn into unmetered paid API traffic.
            throw new BizException(500, "AI 配额服务暂不可用");
        }
    }

    public AiUsageStatsView getUsageStats() {
        String date = LocalDate.now(ZoneOffset.UTC).toString();
        long inflight = 0L;
        long totalRequests = 0L;
        long activeUsers = 0L;
        try {
            String inflightValue = redisTemplate.opsForValue().get(GLOBAL_KEY);
            if (inflightValue != null) {
                inflight = Long.parseLong(inflightValue);
            }
            String totalValue = redisTemplate.opsForValue().get(DAILY_TOTAL_PREFIX + date);
            if (totalValue != null) {
                totalRequests = Long.parseLong(totalValue);
            }
            Long userCount = redisTemplate.opsForSet().size(DAILY_USERS_PREFIX + date);
            if (userCount != null) {
                activeUsers = userCount;
            }
        } catch (Exception e) {
            // Admin dashboard should degrade gracefully when Redis is unavailable.
        }
        return AiUsageStatsView.builder()
                .dailyUserLimit(dailyLimit)
                .globalConcurrencyLimit(globalConcurrency)
                .globalInflight(inflight)
                .todayTotalRequests(totalRequests)
                .todayActiveUsers(activeUsers)
                .usageDate(date)
                .build();
    }
}
