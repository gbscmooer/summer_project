package com.campus.product.ai.dto;

import lombok.Builder;
import lombok.Data;

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
}
