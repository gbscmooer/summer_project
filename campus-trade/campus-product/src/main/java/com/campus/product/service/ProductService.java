package com.campus.product.service;

import com.campus.common.result.PageResult;
import com.campus.product.dto.ProductDetailVO;
import com.campus.product.dto.ProductListVO;
import com.campus.product.dto.ProductRequest;
import com.campus.product.entity.Product;

public interface ProductService {
    Long publish(Long sellerId, ProductRequest request);
    void update(Long sellerId, Long productId, ProductRequest request);
    void remove(Long sellerId, Long productId);

    ProductDetailVO getDetail(Long productId);
    PageResult<ProductListVO> listProducts(String category, Integer pageNum, Integer pageSize);
    PageResult<ProductListVO> myProducts(Long sellerId, Integer pageNum, Integer pageSize);

    // Elasticsearch 搜索
    PageResult<ProductListVO> search(String keyword, String category,
                                     java.math.BigDecimal minPrice, java.math.BigDecimal maxPrice,
                                     String sort, Integer pageNum, Integer pageSize);

    // 运维/一次性：把 MySQL 中 status=1 的全部商品刷入 ES，返回刷入条数
    int reindexAll();

    // 缓存预热：把浏览量 Top N 的商品预热到 Redis 缓存中
    void preheatHotProductsCache();

    // 内部接口（供 OpenFeign）
    Product innerGetProduct(Long productId);
    boolean deductStock(Long productId);
    void restoreStock(Long productId);
}
