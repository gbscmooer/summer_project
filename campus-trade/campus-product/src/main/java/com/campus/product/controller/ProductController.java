package com.campus.product.controller;

import com.campus.common.result.PageResult;
import com.campus.common.result.Result;
import com.campus.product.dto.ProductDetailVO;
import com.campus.product.dto.ProductListVO;
import com.campus.product.dto.ProductRequest;
import com.campus.product.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/product")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    // ==================== 对外接口（经网关） ====================

    @PostMapping
    public Result<Map<String, Long>> publish(
            @RequestHeader("X-User-Id") Long sellerId,
            @Valid @RequestBody ProductRequest request) {
        Long productId = productService.publish(sellerId, request);
        return Result.success("发布成功", Map.of("productId", productId));
    }

    @PutMapping("/{id}")
    public Result<Void> update(
            @RequestHeader("X-User-Id") Long sellerId,
            @PathVariable Long id,
            @Valid @RequestBody ProductRequest request) {
        productService.update(sellerId, id, request);
        return Result.success("修改成功", null);
    }

    @DeleteMapping("/{id}")
    public Result<Void> remove(
            @RequestHeader("X-User-Id") Long sellerId,
            @PathVariable Long id) {
        productService.remove(sellerId, id);
        return Result.success("已下架", null);
    }

    @GetMapping("/{id}")
    public Result<ProductDetailVO> getDetail(@PathVariable Long id) {
        return Result.success(productService.getDetail(id));
    }

    @GetMapping("/list")
    public Result<PageResult<ProductListVO>> list(
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(productService.listProducts(category, pageNum, pageSize));
    }

    @GetMapping("/my")
    public Result<PageResult<ProductListVO>> myProducts(
            @RequestHeader("X-User-Id") Long sellerId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(productService.myProducts(sellerId, pageNum, pageSize));
    }

    // ==================== 内部接口（供 OpenFeign，不经网关） ====================

    @GetMapping("/inner/{id}")
    public Result<ProductDetailVO> innerGetProduct(@PathVariable Long id) {
        return Result.success(ProductDetailVO.from(productService.innerGetProduct(id)));
    }

    @PostMapping("/inner/{id}/deduct")
    public Result<Boolean> deductStock(@PathVariable Long id) {
        return Result.success(productService.deductStock(id));
    }

    @PostMapping("/inner/{id}/restore")
    public Result<Void> restoreStock(@PathVariable Long id) {
        productService.restoreStock(id);
        return Result.success();
    }
}
