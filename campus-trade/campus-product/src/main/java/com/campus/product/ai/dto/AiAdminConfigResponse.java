package com.campus.product.ai.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AiAdminConfigResponse {
    private boolean enabled;
    private String baseUrl;
    /** 脱敏后的 Key，例如 sk-****abcd；未配置时为空 */
    private String apiKeyMasked;
    private boolean apiKeyConfigured;
    private String model;
    private int timeoutSeconds;
    private boolean supportsVision;
    /** 当前实际生效来源：env / admin */
    private String activeSource;
    private String envBaseUrl;
    private String envModel;
    private int envTimeoutSeconds;
    private boolean envSupportsVision;
    private String envApiKeyMasked;
    private boolean envApiKeyConfigured;
    /** 当前运行时实际使用的配置（与 activeSource 对应） */
    private String runtimeBaseUrl;
    private String runtimeModel;
    private int runtimeTimeoutSeconds;
    private boolean runtimeSupportsVision;
    private boolean runtimeKeyConfigured;
    /** 管理端已保存配置但因未启用覆盖而未生效 */
    private boolean savedButDisabled;
    private LocalDateTime configUpdatedAt;
    private AiHealthStatusView health;
    private AiUsageStatsView usage;
}
