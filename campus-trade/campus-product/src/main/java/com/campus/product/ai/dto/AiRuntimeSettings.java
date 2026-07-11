package com.campus.product.ai.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 运行时生效的 AI 连接参数（环境变量默认值 + 管理员覆盖合并后）。
 */
@Data
@Builder
public class AiRuntimeSettings {
    private String baseUrl;
    private String apiKey;
    private String model;
    private int timeoutSeconds;
    private boolean supportsVision;
    /** env 或 admin */
    private String source;
}
