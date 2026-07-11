package com.campus.product.runner;

import com.campus.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 启动时把 MySQL 在售有库存商品刷入 ES。
 * 种子 SQL / 手工导入不会走 publish 双写，不刷索引会导致 AI 搜索与首页不一致。
 */
@Component
@ConditionalOnProperty(name = "campus.search.reindex-on-startup", havingValue = "true")
@Order(1)
@Slf4j
@RequiredArgsConstructor
public class ProductEsReindexRunner implements CommandLineRunner {

    private final ProductService productService;

    @Override
    public void run(String... args) {
        try {
            int count = productService.reindexAll();
            log.info("启动 ES 全量同步完成，共 {} 条", count);
        } catch (Exception e) {
            log.error("启动 ES 全量同步失败（搜索将在可用时降级 DB）", e);
        }
    }
}
