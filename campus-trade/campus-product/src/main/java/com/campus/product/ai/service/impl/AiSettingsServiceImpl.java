package com.campus.product.ai.service.impl;

import com.campus.common.exception.BizException;
import com.campus.common.result.ResultCode;
import com.campus.product.ai.dto.AiAdminConfigRequest;
import com.campus.product.ai.dto.AiAdminConfigResponse;
import com.campus.product.ai.dto.AiRuntimeSettings;
import com.campus.product.ai.client.AiEndpointValidator;
import com.campus.product.ai.service.AiSettingsService;
import com.campus.product.ai.service.AiSecretCrypto;
import com.campus.product.config.AiProperties;
import com.campus.product.entity.AiConfigEntity;
import com.campus.product.mapper.AiConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AiSettingsServiceImpl implements AiSettingsService {

    public static final long CONFIG_ID = 1L;

    private final AiProperties envProperties;
    private final AiConfigMapper aiConfigMapper;
    private final AiSecretCrypto secretCrypto;

    @Override
    public AiRuntimeSettings resolve() {
        AiConfigEntity override = loadOverride();
        if (override != null && Integer.valueOf(1).equals(override.getEnabled())) {
            String baseUrl = firstNonBlank(override.getBaseUrl(), envProperties.getBaseUrl());
            String apiKey = sameEndpoint(override.getApiKeyBaseUrl(), baseUrl)
                    ? secretCrypto.decrypt(override.getApiKey()) : null;
            if (!StringUtils.hasText(apiKey) && sameEndpoint(baseUrl, envProperties.getBaseUrl())) {
                apiKey = envProperties.getApiKey();
            }
            return AiRuntimeSettings.builder()
                    .baseUrl(baseUrl)
                    .apiKey(apiKey)
                    .model(firstNonBlank(override.getModel(), envProperties.getModel()))
                    .timeoutSeconds(override.getTimeoutSeconds() != null
                            ? boundedTimeout(override.getTimeoutSeconds())
                            : boundedTimeout(envProperties.getTimeoutSeconds()))
                    .supportsVision(override.getSupportsVision() != null
                            ? Integer.valueOf(1).equals(override.getSupportsVision())
                            : envProperties.isSupportsVision())
                    .source("admin")
                    .build();
        }
        return fromEnv();
    }

    @Override
    public AiAdminConfigResponse getAdminView() {
        AiRuntimeSettings active = resolve();
        AiConfigEntity override = loadOverride();
        boolean enabled = override != null && Integer.valueOf(1).equals(override.getEnabled());
        String overrideBaseUrl = override == null ? null
                : firstNonBlank(override.getBaseUrl(), envProperties.getBaseUrl());
        String storedKey = enabled && override != null
                && sameEndpoint(override.getApiKeyBaseUrl(), overrideBaseUrl)
                ? secretCrypto.decrypt(override.getApiKey()) : null;
        String displayKey = StringUtils.hasText(storedKey) ? storedKey : active.getApiKey();
        return AiAdminConfigResponse.builder()
                .enabled(enabled)
                .baseUrl(enabled && override != null && StringUtils.hasText(override.getBaseUrl())
                        ? override.getBaseUrl() : active.getBaseUrl())
                .apiKeyMasked(maskKey(displayKey))
                .apiKeyConfigured(StringUtils.hasText(displayKey))
                .model(enabled && override != null && StringUtils.hasText(override.getModel())
                        ? override.getModel() : active.getModel())
                .timeoutSeconds(active.getTimeoutSeconds())
                .supportsVision(active.isSupportsVision())
                .activeSource(active.getSource())
                .envBaseUrl(envProperties.getBaseUrl())
                .envModel(envProperties.getModel())
                .build();
    }

    @Override
    public AiAdminConfigResponse saveAdminConfig(Long adminId, AiAdminConfigRequest request) {
        if (request == null) {
            throw new BizException(ResultCode.BAD_REQUEST);
        }
        AiConfigEntity existing = aiConfigMapper.selectById(CONFIG_ID);
        String previousBaseUrl = existing == null
                ? envProperties.getBaseUrl()
                : firstNonBlank(existing.getBaseUrl(), envProperties.getBaseUrl());
        AiConfigEntity entity = existing == null ? new AiConfigEntity() : existing;
        entity.setId(CONFIG_ID);
        entity.setEnabled(Boolean.TRUE.equals(request.getEnabled()) ? 1 : 0);
        if (StringUtils.hasText(request.getBaseUrl())) {
            entity.setBaseUrl(AiEndpointValidator.requireSafePublicHttpsUrl(request.getBaseUrl()));
        }
        String submittedKey = request.getApiKey() == null ? null : request.getApiKey().trim();
        if (StringUtils.hasText(request.getModel())) {
            entity.setModel(request.getModel().trim());
        }
        if (request.getTimeoutSeconds() != null) {
            entity.setTimeoutSeconds(request.getTimeoutSeconds());
        }
        if (request.getSupportsVision() != null) {
            entity.setSupportsVision(Boolean.TRUE.equals(request.getSupportsVision()) ? 1 : 0);
        }
        entity.setUpdatedBy(adminId);
        entity.setUpdateTime(LocalDateTime.now());

        String targetBaseUrl = AiEndpointValidator.requireSafePublicHttpsUrl(
                firstNonBlank(entity.getBaseUrl(), envProperties.getBaseUrl()));
        boolean endpointChanged = !sameEndpoint(targetBaseUrl, previousBaseUrl);
        boolean suppliedNewKey = StringUtils.hasText(submittedKey);
        if (suppliedNewKey) {
            entity.setApiKey(secretCrypto.encrypt(submittedKey));
            entity.setApiKeyBaseUrl(targetBaseUrl);
        } else if (endpointChanged) {
            // A key must never survive an endpoint change, even while the override is disabled.
            entity.setApiKey(null);
            entity.setApiKeyBaseUrl(null);
        }
        boolean hasStoredKey = StringUtils.hasText(entity.getApiKey())
                && sameEndpoint(entity.getApiKeyBaseUrl(), targetBaseUrl);
        boolean mayUseEnvKey = sameEndpoint(targetBaseUrl, envProperties.getBaseUrl());
        if (Integer.valueOf(1).equals(entity.getEnabled()) && !hasStoredKey && !mayUseEnvKey) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "修改 AI API 地址时必须同时提供对应的新 API Key");
        }

        if (existing == null) {
            if (!StringUtils.hasText(entity.getBaseUrl())) {
                entity.setBaseUrl(envProperties.getBaseUrl());
            }
            if (!StringUtils.hasText(entity.getModel())) {
                entity.setModel(envProperties.getModel());
            }
            if (entity.getTimeoutSeconds() == null) {
                entity.setTimeoutSeconds(boundedTimeout(envProperties.getTimeoutSeconds()));
            }
            if (entity.getSupportsVision() == null) {
                entity.setSupportsVision(envProperties.isSupportsVision() ? 1 : 0);
            }
            aiConfigMapper.insert(entity);
        } else {
            aiConfigMapper.updateById(entity);
        }
        return getAdminView();
    }

    private AiRuntimeSettings fromEnv() {
        return AiRuntimeSettings.builder()
                .baseUrl(envProperties.getBaseUrl())
                .apiKey(envProperties.getApiKey())
                .model(envProperties.getModel())
                .timeoutSeconds(boundedTimeout(envProperties.getTimeoutSeconds()))
                .supportsVision(envProperties.isSupportsVision())
                .source("env")
                .build();
    }

    private AiConfigEntity loadOverride() {
        AiConfigEntity value = aiConfigMapper.selectById(CONFIG_ID);
        if (value != null && StringUtils.hasText(value.getApiKey())
                && !secretCrypto.isEncrypted(value.getApiKey())) {
                    value.setApiKey(secretCrypto.encrypt(value.getApiKey()));
                    // Legacy plaintext has no cryptographic endpoint binding. Encrypt it at rest,
                    // but require an administrator to submit it again before it can be used.
                    value.setApiKeyBaseUrl(null);
                    aiConfigMapper.updateById(value);
        }
        return value;
    }

    private static String firstNonBlank(String preferred, String fallback) {
        return StringUtils.hasText(preferred) ? preferred : fallback;
    }

    private static boolean sameEndpoint(String first, String second) {
        if (!StringUtils.hasText(first) || !StringUtils.hasText(second)) {
            return false;
        }
        return first.trim().replaceAll("/+$", "")
                .equalsIgnoreCase(second.trim().replaceAll("/+$", ""));
    }

    private static int boundedTimeout(int seconds) {
        return Math.max(5, Math.min(85, seconds));
    }

    public static String maskKey(String apiKey) {
        if (!StringUtils.hasText(apiKey)) {
            return "";
        }
        String value = apiKey.trim();
        if (value.length() <= 4) {
            return "****";
        }
        if (value.length() <= 8) {
            return "****" + value.substring(value.length() - 2);
        }
        return value.substring(0, Math.min(3, value.length())) + "****" + value.substring(value.length() - 4);
    }
}
