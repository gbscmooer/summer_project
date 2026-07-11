package com.campus.product.ai.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AiAdminConfigRequest {
    /** true=启用管理员覆盖；false=回退环境变量 */
    private Boolean enabled;

    @Size(max = 255, message = "API 地址过长")
    private String baseUrl;

    @Size(max = 512, message = "API Key 过长")
    private String apiKey;

    @Size(max = 100, message = "模型名过长")
    private String model;

    @Min(value = 5, message = "超时至少5秒")
    @Max(value = 85, message = "超时最多85秒")
    private Integer timeoutSeconds;

    private Boolean supportsVision;
}
