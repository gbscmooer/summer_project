package com.campus.product.controller;

import com.campus.common.result.PageResult;
import com.campus.common.result.Result;
import com.campus.product.dto.ProductCommentRequest;
import com.campus.product.dto.ProductCommentVO;
import com.campus.product.service.ProductCommentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/product")
@RequiredArgsConstructor
public class ProductCommentController {

    private final ProductCommentService productCommentService;

    @GetMapping("/{productId}/comments")
    public Result<PageResult<ProductCommentVO>> listComments(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.success(productCommentService.listByProduct(productId, pageNum, pageSize));
    }

    @PostMapping("/{productId}/comments")
    public Result<Map<String, Long>> addComment(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long productId,
            @Valid @RequestBody ProductCommentRequest request) {
        Long commentId = productCommentService.addComment(userId, productId, request);
        return Result.success("留言成功", Map.of("commentId", commentId));
    }
}
