package com.campus.product.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({AiProperties.class, ProductImageProperties.class})
public class AiConfig {
}
