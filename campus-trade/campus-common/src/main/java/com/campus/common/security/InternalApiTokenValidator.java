package com.campus.common.security;

import com.campus.common.exception.BizException;
import com.campus.common.result.ResultCode;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Validates the shared token used only for service-to-service HTTP calls. */
@Component
public class InternalApiTokenValidator {

    public static final String HEADER_NAME = "X-Internal-Token";
    private static final int MIN_TOKEN_BYTES = 32;

    private final String expectedToken;

    public InternalApiTokenValidator(@Value("${campus.internal-api-token:}") String expectedToken) {
        this.expectedToken = expectedToken == null ? "" : expectedToken;
    }

    @PostConstruct
    void validateConfiguration() {
        if (expectedToken.getBytes(StandardCharsets.UTF_8).length < MIN_TOKEN_BYTES) {
            throw new IllegalStateException("INTERNAL_API_TOKEN must contain at least 32 UTF-8 bytes");
        }
    }

    public void requireValid(String candidate) {
        byte[] expected = expectedToken.getBytes(StandardCharsets.UTF_8);
        byte[] actual = candidate == null ? new byte[0] : candidate.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
    }
}
