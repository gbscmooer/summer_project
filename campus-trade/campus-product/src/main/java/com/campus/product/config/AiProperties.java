package com.campus.product.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "campus.ai")
public class AiProperties {
    private String baseUrl = "https://api.openai.com/v1";
    private String apiKey;
    private String model = "gpt-4.1-mini";
    private int timeoutSeconds = 60;
    private boolean supportsVision = true;
}
