package com.campus.product.controller;

import com.campus.common.result.PageResult;
import com.campus.common.result.Result;
import com.campus.product.dto.FavoriteProductVO;
import com.campus.product.dto.FavoriteStatusVO;
import com.campus.product.service.ProductFavoriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/product")
@RequiredArgsConstructor
public class ProductFavoriteController {

    private final ProductFavoriteService productFavoriteService;

    @PostMapping("/{productId}/favorite/toggle")
    public Result<FavoriteStatusVO> toggle(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long productId) {
        return Result.success(productFavoriteService.toggle(userId, productId));
    }

    @GetMapping("/{productId}/favorite/status")
    public Result<FavoriteStatusVO> status(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long productId) {
        return Result.success(productFavoriteService.status(userId, productId));
    }

    @GetMapping("/favorites/mine")
    public Result<PageResult<FavoriteProductVO>> listMine(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.success(productFavoriteService.listMine(userId, pageNum, pageSize));
    }
}
