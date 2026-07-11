package com.campus.user;

import com.campus.common.util.JwtUtil;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@EnableDiscoveryClient
@ComponentScan(basePackages = {"com.campus.user", "com.campus.common"})
public class CampusUserApplication {
    public static void main(String[] args) {
        JwtUtil.validateConfiguration();
        SpringApplication.run(CampusUserApplication.class, args);
    }
}
