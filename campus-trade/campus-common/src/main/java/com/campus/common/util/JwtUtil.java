package com.campus.common.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

public class JwtUtil {

    private static final String SECRET_ENV = "JWT_SECRET";
    private static final String SECRET_PROPERTY = "campus.jwt-secret";
    private static final int MIN_SECRET_BYTES = 32;
    private static final String EXPIRATION_ENV = "JWT_EXPIRATION_MINUTES";
    private static final long DEFAULT_EXPIRATION_MINUTES = 120;
    private static final String ISSUER = "campus-user";
    private static final String AUDIENCE = "campus-gateway";
    private static final String USER_ID_CLAIM = "userId";

    private static final SecretKey KEY = loadKey();

    private JwtUtil() {
    }

    /**
     * Forces secret resolution during service startup so a deployment with a missing or weak
     * signing key fails closed instead of issuing unusable or forgeable tokens.
     */
    public static void validateConfiguration() {
        // Accessing the eagerly initialized key is enough; keep this method explicit for callers.
        if (KEY.getEncoded().length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT signing key is too short");
        }
    }

    public static String generateToken(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        return Jwts.builder()
                .claim(USER_ID_CLAIM, userId)
                .id(UUID.randomUUID().toString())
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMillis()))
                .signWith(KEY)
                .compact();
    }

    public static Long parseUserId(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(KEY)
                .requireIssuer(ISSUER)
                .requireAudience(AUDIENCE)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        Long userId = claims.get(USER_ID_CLAIM, Long.class);
        if (userId == null) {
            throw new JwtException("User ID is null in token claims");
        }
        return userId;
    }

    public static boolean isValid(String token) {
        try {
            parseUserId(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private static SecretKey loadKey() {
        String secret = System.getProperty(SECRET_PROPERTY);
        if (secret == null || secret.isBlank()) {
            secret = System.getenv(SECRET_ENV);
        }
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET is required");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET must contain at least 32 UTF-8 bytes");
        }
        return Keys.hmacShaKeyFor(bytes);
    }

    private static long expirationMillis() {
        String raw = System.getenv(EXPIRATION_ENV);
        long minutes = DEFAULT_EXPIRATION_MINUTES;
        if (raw != null && !raw.isBlank()) {
            try {
                minutes = Long.parseLong(raw);
            } catch (NumberFormatException e) {
                throw new IllegalStateException("JWT_EXPIRATION_MINUTES must be an integer", e);
            }
        }
        if (minutes < 5 || minutes > 1440) {
            throw new IllegalStateException("JWT_EXPIRATION_MINUTES must be between 5 and 1440");
        }
        return minutes * 60_000L;
    }
}
