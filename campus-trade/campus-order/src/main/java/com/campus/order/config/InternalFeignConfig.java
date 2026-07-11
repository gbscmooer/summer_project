package com.campus.order.config;

import com.campus.common.security.InternalApiTokenValidator;
import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InternalFeignConfig {

    @Bean
    RequestInterceptor internalApiTokenInterceptor(
            @Value("${campus.internal-api-token}") String internalApiToken) {
        return template -> template.header(InternalApiTokenValidator.HEADER_NAME, internalApiToken);
    }
}
