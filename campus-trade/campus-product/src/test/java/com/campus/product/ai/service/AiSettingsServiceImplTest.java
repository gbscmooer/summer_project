package com.campus.product.ai.service;

import com.campus.product.ai.dto.AiAdminConfigRequest;
import com.campus.product.ai.dto.AiAdminConfigResponse;
import com.campus.product.ai.dto.AiRuntimeSettings;
import com.campus.product.ai.dto.AiUsageStatsView;
import com.campus.product.ai.service.impl.AiSettingsServiceImpl;
import com.campus.product.config.AiProperties;
import com.campus.product.entity.AiConfigEntity;
import com.campus.product.mapper.AiConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiSettingsServiceImplTest {

    private AiProperties env;
    private AiConfigMapper mapper;
    private AiSettingsService service;
    private AiSecretCrypto crypto;

    @BeforeEach
    void setUp() {
        env = new AiProperties();
        env.setBaseUrl("https://api.openai.com/v1");
        env.setApiKey("env-secret-key");
        env.setModel("gpt-4.1-mini");
        env.setTimeoutSeconds(60);
        env.setSupportsVision(true);
        mapper = mock(AiConfigMapper.class);
        crypto = new AiSecretCrypto("test-master-secret-at-least-32-bytes-long");
        AiUsageGuard usageGuard = mock(AiUsageGuard.class);
        AiHealthProbeService healthProbe = mock(AiHealthProbeService.class);
        when(usageGuard.getUsageStats()).thenReturn(AiUsageStatsView.builder()
                .dailyUserLimit(100)
                .globalConcurrencyLimit(8)
                .build());
        service = new AiSettingsServiceImpl(env, mapper, crypto, usageGuard, healthProbe);
    }

    @Test
    void fallsBackToEnvWhenAdminOverrideDisabled() {
        when(mapper.selectById(1L)).thenReturn(null);
        AiRuntimeSettings settings = service.resolve();
        assertEquals("env", settings.getSource());
        assertEquals("env-secret-key", settings.getApiKey());
    }

    @Test
    void usesAdminOverrideWhenEnabled() {
        AiConfigEntity row = new AiConfigEntity();
        row.setId(1L);
        row.setEnabled(1);
        row.setBaseUrl("https://api.deepseek.com");
        row.setApiKey(crypto.encrypt("admin-key-123456"));
        row.setApiKeyBaseUrl("https://api.deepseek.com");
        row.setModel("deepseek-v4-flash");
        row.setTimeoutSeconds(45);
        row.setSupportsVision(0);
        when(mapper.selectById(1L)).thenReturn(row);

        AiRuntimeSettings settings = service.resolve();
        assertEquals("admin", settings.getSource());
        assertEquals("https://api.deepseek.com", settings.getBaseUrl());
        assertEquals("admin-key-123456", settings.getApiKey());
        assertFalse(settings.isSupportsVision());
    }

    @Test
    void masksStoredKeyEvenWhenOverrideDisabled() {
        AiConfigEntity row = new AiConfigEntity();
        row.setId(1L);
        row.setEnabled(0);
        row.setBaseUrl("https://api.deepseek.com");
        row.setApiKey(crypto.encrypt("admin-key-123456"));
        row.setApiKeyBaseUrl("https://api.deepseek.com");
        row.setModel("deepseek-v4-flash");
        when(mapper.selectById(1L)).thenReturn(row);

        AiAdminConfigResponse view = service.getAdminView();
        assertTrue(view.getApiKeyMasked().contains("****"));
        assertTrue(view.isSavedButDisabled());
        assertEquals("env", view.getActiveSource());
        assertEquals("https://api.deepseek.com", view.getBaseUrl());
    }

    @Test
    void savingCustomEndpointWithStoredKeyAutoEnables() {
        AiConfigEntity row = new AiConfigEntity();
        row.setId(1L);
        row.setEnabled(0);
        row.setBaseUrl("https://api.deepseek.com");
        row.setApiKey(crypto.encrypt("admin-key-123456"));
        row.setApiKeyBaseUrl("https://api.deepseek.com");
        row.setModel("deepseek-v4-flash");
        when(mapper.selectById(1L)).thenReturn(row);

        AiAdminConfigRequest request = new AiAdminConfigRequest();
        request.setEnabled(false);
        request.setBaseUrl("https://api.deepseek.com");
        request.setModel("deepseek-v4-flash");
        request.setApiKey("");

        AiAdminConfigResponse saved = service.saveAdminConfig(99L, request);
        assertTrue(saved.isEnabled());
        assertEquals("admin", saved.getActiveSource());
    }

    @Test
    void savesAdminConfig() {
        when(mapper.selectById(1L)).thenReturn(null);
        AiAdminConfigRequest request = new AiAdminConfigRequest();
        request.setEnabled(true);
        request.setBaseUrl("https://api.deepseek.com");
        request.setApiKey("sk-new-key-9999");
        request.setModel("deepseek-v4-flash");
        request.setTimeoutSeconds(50);
        request.setSupportsVision(false);

        service.saveAdminConfig(99L, request);
        verify(mapper).insert(any(AiConfigEntity.class));
    }

    @Test
    void maskKeyKeepsTail() {
        assertEquals("sk-****9999", AiSettingsServiceImpl.maskKey("sk-abcdefgh9999"));
    }

    @Test
    void changingEndpointRequiresNewBoundKey() {
        AiConfigEntity row = new AiConfigEntity();
        row.setId(1L);
        row.setEnabled(1);
        row.setBaseUrl("https://api.openai.com/v1");
        row.setApiKey(crypto.encrypt("old-provider-key"));
        row.setApiKeyBaseUrl("https://api.openai.com/v1");
        when(mapper.selectById(1L)).thenReturn(row);

        AiAdminConfigRequest request = new AiAdminConfigRequest();
        request.setEnabled(true);
        request.setBaseUrl("https://api.deepseek.com");
        request.setApiKey("");

        assertThrows(com.campus.common.exception.BizException.class,
                () -> service.saveAdminConfig(99L, request));
    }

    @Test
    void disabledEndpointChangeCannotReuseOldKeyWhenReenabled() {
        AiConfigEntity row = new AiConfigEntity();
        row.setId(1L);
        row.setEnabled(1);
        row.setBaseUrl("https://api.openai.com/v1");
        row.setApiKey(crypto.encrypt("old-provider-key"));
        row.setApiKeyBaseUrl("https://api.openai.com/v1");
        when(mapper.selectById(1L)).thenReturn(row);

        AiAdminConfigRequest disableAndChange = new AiAdminConfigRequest();
        disableAndChange.setEnabled(false);
        disableAndChange.setBaseUrl("https://api.deepseek.com");
        disableAndChange.setApiKey("");
        service.saveAdminConfig(99L, disableAndChange);
        assertFalse(org.springframework.util.StringUtils.hasText(row.getApiKey()));

        AiAdminConfigRequest reenable = new AiAdminConfigRequest();
        reenable.setEnabled(true);
        reenable.setApiKey("");
        assertThrows(com.campus.common.exception.BizException.class,
                () -> service.saveAdminConfig(99L, reenable));
    }

    @Test
    void legacyPlaintextIsEncryptedButNotTrustedWithoutEndpointBinding() {
        AiConfigEntity row = new AiConfigEntity();
        row.setId(1L);
        row.setEnabled(1);
        row.setBaseUrl("https://api.deepseek.com");
        row.setApiKey("legacy-plaintext-key");
        when(mapper.selectById(1L)).thenReturn(row);

        AiRuntimeSettings settings = service.resolve();
        assertTrue(crypto.isEncrypted(row.getApiKey()));
        assertFalse(org.springframework.util.StringUtils.hasText(settings.getApiKey()));
        verify(mapper).updateById(row);
    }
}
