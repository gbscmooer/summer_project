package com.campus.common.security;

import com.campus.common.exception.BizException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InternalApiTokenValidatorTest {

    @Test
    void acceptsOnlyExactStrongToken() {
        String token = "internal-test-token-at-least-32-bytes-long";
        InternalApiTokenValidator validator = new InternalApiTokenValidator(token);
        validator.validateConfiguration();

        assertDoesNotThrow(() -> validator.requireValid(token));
        assertThrows(BizException.class, () -> validator.requireValid(token + "x"));
        assertThrows(BizException.class, () -> validator.requireValid(null));
    }

    @Test
    void rejectsWeakDeploymentToken() {
        InternalApiTokenValidator validator = new InternalApiTokenValidator("weak");
        assertThrows(IllegalStateException.class, validator::validateConfiguration);
    }
}
