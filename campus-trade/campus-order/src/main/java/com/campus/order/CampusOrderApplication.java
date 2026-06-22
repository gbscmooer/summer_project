package com.campus.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

/**
 * 订单服务启动类（端口 8083）。
 *
 * <p>P4 阶段仅搭建微服务骨架：注册 Nacos、开启 OpenFeign，复用 campus-common 的
 * {@code GlobalExceptionHandler}（order 是 Servlet Web 服务，无 WebFlux 冲突）。
 * 真正的下单业务逻辑在 P5 实现。
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@ComponentScan(basePackages = {"com.campus.order", "com.campus.common"})
public class CampusOrderApplication {
    public static void main(String[] args) {
        SpringApplication.run(CampusOrderApplication.class, args);
    }
}
