package com.campus.user.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class OnboardingFlagCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> ALLOWED_STEPS = Set.of("browse", "ai", "notify", "profile");

    private OnboardingFlagCodec() {
    }

    public static boolean isAllowedStep(String step) {
        return step != null && ALLOWED_STEPS.contains(step);
    }

    public static Map<String, Boolean> decode(String raw) {
        if (!StringUtils.hasText(raw)) {
            return new HashMap<>();
        }
        try {
            Map<String, Boolean> parsed = MAPPER.readValue(raw, new TypeReference<>() {});
            return parsed == null ? new HashMap<>() : new HashMap<>(parsed);
        } catch (JsonProcessingException e) {
            return new HashMap<>();
        }
    }

    public static String encode(Map<String, Boolean> flags) {
        Map<String, Boolean> safe = flags == null ? new HashMap<>() : new HashMap<>(flags);
        try {
            return MAPPER.writeValueAsString(safe);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
