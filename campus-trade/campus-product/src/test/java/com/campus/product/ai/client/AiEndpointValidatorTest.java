package com.campus.product.ai.client;

import com.campus.common.exception.BizException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class AiEndpointValidatorTest {

    @Test
    void rejectsLocalAndNonHttpsEndpoints() {
        assertThrows(BizException.class,
                () -> AiEndpointValidator.requireSafePublicHttpsUrl("http://api.openai.com/v1"));
        assertThrows(BizException.class,
                () -> AiEndpointValidator.requireSafePublicHttpsUrl("https://127.0.0.1/v1"));
        assertThrows(BizException.class,
                () -> AiEndpointValidator.requireSafePublicHttpsUrl("https://[::1]/v1"));
        assertThrows(BizException.class,
                () -> AiEndpointValidator.requireSafePublicHttpsUrl("https://169.254.169.254/latest/meta-data"));
    }

    @Test
    void appendsV1WhenBaseUrlHasNoPath() {
        org.junit.jupiter.api.Assertions.assertEquals(
                "https://api.example.com/v1",
                AiEndpointValidator.normalizeOpenAiCompatibleBaseUrl("https://api.example.com"));
    }
}
