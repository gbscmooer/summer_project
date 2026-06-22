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

    // 内部接口（供 OpenFeign）
    Product innerGetProduct(Long productId);
    boolean deductStock(Long productId);
    void restoreStock(Long productId);
}
