package com.campus.common.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    static {
        System.setProperty("campus.jwt-secret", "unit-test-jwt-secret-at-least-32-bytes-long");
    }

    @Test
    void generateAndParseToken() {
        Long userId = 1001L;
        String token = JwtUtil.generateToken(userId);

        assertNotNull(token);
        assertTrue(token.split("\\.").length == 3, "JWT应由3段组成");

        Long parsed = JwtUtil.parseUserId(token);
        assertEquals(userId, parsed, "解析出的userId应与生成时一致");
    }

    @Test
    void generateTokenWithNullUserId() {
        assertThrows(IllegalArgumentException.class, () -> JwtUtil.generateToken(null));
    }

    @Test
    void invalidToken() {
        assertFalse(JwtUtil.isValid("invalid.token.here"));
        assertFalse(JwtUtil.isValid(""));
    }
}
