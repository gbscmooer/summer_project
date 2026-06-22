package com.campus.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.campus.user", "com.campus.common"})
public class CampusUserApplication {
    public static void main(String[] args) {
        SpringApplication.run(CampusUserApplication.class, args);
    }
}
