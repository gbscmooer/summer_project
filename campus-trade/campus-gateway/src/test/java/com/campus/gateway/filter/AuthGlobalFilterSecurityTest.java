package com.campus.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthGlobalFilterSecurityTest {

    private final AuthGlobalFilter filter = new AuthGlobalFilter();

    @Test
    void blocksAllInternalRoutesAtPublicGateway() {
        assertTrue(filter.isInternalPath("/api/user/batch"));
        assertTrue(filter.isInternalPath("/api/user/internal/role"));
        assertTrue(filter.isInternalPath("/api/product/inner/1"));
        assertTrue(filter.isInternalPath("/api/product/inner/1/restore"));
        assertFalse(filter.isInternalPath("/api/product/1"));
    }

    @Test
    void whitelistIsBoundToExpectedHttpMethod() {
        assertTrue(filter.isWhiteList("/api/user/login", HttpMethod.POST));
        assertFalse(filter.isWhiteList("/api/user/login", HttpMethod.GET));
        assertTrue(filter.isWhiteList("/api/product/list", HttpMethod.GET));
        assertFalse(filter.isWhiteList("/api/product/list", HttpMethod.POST));
    }
}
