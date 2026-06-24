package com.campus.product.runner;

import com.campus.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 缓存预热启动器 (T6.2)。
 * 在服务启动完毕后，自动加载浏览量 Top N 的热点商品至 Redis，防缓存击穿/冷启动。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ProductCachePreheatRunner implements CommandLineRunner {

    private final ProductService productService;

    @Override
    public void run(String... args) throws Exception {
        try {
            productService.preheatHotProductsCache();
        } catch (Exception e) {
            log.error("启动预热热门商品缓存异常", e);
        }
    }
}
