package com.campus.gateway;

import com.campus.common.util.JwtUtil;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * API 网关启动类（端口 8080）。
 *
 * <p>注意：仅扫描 {@code com.campus.gateway}，刻意不扫描 {@code com.campus.common}。
 * 因为 campus-common 中的 {@code GlobalExceptionHandler} 是基于 Servlet 的
 * {@code @RestControllerAdvice}，而本网关是 Spring WebFlux（响应式）应用，
 * 若被扫描成 Bean 会与 WebFlux 模型冲突。JwtUtil 是静态工具类，直接调用即可，无需扫描成 Bean。
 */
@SpringBootApplication(scanBasePackages = "com.campus.gateway")
public class CampusGatewayApplication {
    public static void main(String[] args) {
        JwtUtil.validateConfiguration();
        SpringApplication.run(CampusGatewayApplication.class, args);
    }
}
