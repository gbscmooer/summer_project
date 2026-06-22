package com.campus.product;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.campus.product", "com.campus.common"})
public class CampusProductApplication {
    public static void main(String[] args) {
        SpringApplication.run(CampusProductApplication.class, args);
    }
}
