package com.campus.product.ai.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class AiSecretCryptoTest {

    @Test
    void encryptsWithAuthenticatedEncryption() {
        AiSecretCrypto crypto = new AiSecretCrypto("test-master-secret-at-least-32-bytes-long");
        String encrypted = crypto.encrypt("sk-sensitive-value");

        assertNotEquals("sk-sensitive-value", encrypted);
        assertFalse(encrypted.contains("sk-sensitive-value"));
        assertEquals("sk-sensitive-value", crypto.decrypt(encrypted));
    }
}
