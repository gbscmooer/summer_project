package com.campus.gateway.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CorsConfigTest {

    @Test
    void corsAllowsOnlyGetPostAndPreflight() {
        List<String> methods = CorsConfig.allowedMethods();

        assertEquals(List.of("GET", "POST", "OPTIONS"), methods);
        assertFalse(methods.contains("PUT"));
        assertFalse(methods.contains("DELETE"));
        assertFalse(methods.contains("PATCH"));
    }
}
