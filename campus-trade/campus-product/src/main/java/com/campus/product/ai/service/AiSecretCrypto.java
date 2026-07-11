package com.campus.product.ai.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** Encrypts administrator-managed AI keys before they are persisted. */
@Component
public class AiSecretCrypto {

    private static final String PREFIX = "enc:v1:";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final String masterSecret;
    private SecretKeySpec key;

    public AiSecretCrypto(@Value("${campus.ai.config-encryption-key:}") String masterSecret) {
        this.masterSecret = masterSecret == null ? "" : masterSecret;
    }

    @PostConstruct
    void initialize() {
        if (masterSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("AI_CONFIG_ENCRYPTION_KEY must contain at least 32 UTF-8 bytes");
        }
        try {
            key = new SecretKeySpec(MessageDigest.getInstance("SHA-256")
                    .digest(masterSecret.getBytes(StandardCharsets.UTF_8)), "AES");
        } catch (Exception e) {
            throw new IllegalStateException("Unable to initialize AI key encryption", e);
        }
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return plaintext;
        }
        if (isEncrypted(plaintext)) {
            return plaintext;
        }
        ensureInitialized();
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            RANDOM.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] payload = ByteBuffer.allocate(nonce.length + encrypted.length)
                    .put(nonce).put(encrypted).array();
            return PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to encrypt AI API key", e);
        }
    }

    public String decrypt(String storedValue) {
        if (storedValue == null || storedValue.isBlank() || !isEncrypted(storedValue)) {
            return storedValue;
        }
        ensureInitialized();
        try {
            byte[] payload = Base64.getDecoder().decode(storedValue.substring(PREFIX.length()));
            if (payload.length <= NONCE_BYTES) {
                throw new IllegalArgumentException("Invalid encrypted payload");
            }
            byte[] nonce = new byte[NONCE_BYTES];
            byte[] encrypted = new byte[payload.length - NONCE_BYTES];
            System.arraycopy(payload, 0, nonce, 0, NONCE_BYTES);
            System.arraycopy(payload, NONCE_BYTES, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to decrypt AI API key", e);
        }
    }

    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    private void ensureInitialized() {
        if (key == null) {
            initialize();
        }
    }
}
