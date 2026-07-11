package com.campus.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.Collections;

/**
 * WebFlux 全局 CORS 配置，与 Spring Cloud Gateway 路由兼容。
 */
@Configuration
public class CorsConfig {

    private final java.util.List<String> allowedOrigins;

    public CorsConfig(@Value("${CORS_ALLOWED_ORIGINS:http://localhost:5173,http://127.0.0.1:5173}") String origins) {
        this.allowedOrigins = Arrays.stream(origins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
    }

    static java.util.List<String> allowedMethods() {
        return Arrays.asList("GET", "POST", "OPTIONS");
    }

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        // Authentication uses an explicit Bearer header, not ambient browser cookies.
        config.setAllowCredentials(false);
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(allowedMethods());
        config.setAllowedHeaders(Arrays.asList(
                "Authorization",
                "Content-Type",
                "X-Requested-With",
                "Accept",
                "Origin"
        ));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }
}
