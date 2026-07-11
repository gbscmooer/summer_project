package com.campus.product.ai.service;

import com.campus.common.exception.BizException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiUsageGuardTest {

    @Test
    @SuppressWarnings("unchecked")
    void enforcesDailyUserLimitBeforeAiWork() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.increment(anyString())).thenReturn(100L, 101L);
        AiUsageGuard guard = new AiUsageGuard(redis, 100, 8);

        assertDoesNotThrow(() -> guard.requireQuota(7L));
        assertThrows(BizException.class, () -> guard.requireQuota(7L));
    }
}
