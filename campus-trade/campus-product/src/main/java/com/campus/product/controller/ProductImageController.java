package com.campus.product.controller;

import com.campus.common.result.Result;
import com.campus.product.service.ProductImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/product/image")
@RequiredArgsConstructor
public class ProductImageController {

    private final ProductImageService productImageService;

    @PostMapping("/upload")
    public Result<Map<String, List<String>>> upload(
            @RequestHeader("X-User-Id") Long sellerId,
            @RequestParam("files") List<MultipartFile> files) {
        return Result.success(Map.of("images", productImageService.store(files, sellerId)));
    }

    @PostMapping("/delete")
    public Result<Void> delete(
            @RequestHeader("X-User-Id") Long sellerId,
            @RequestBody Map<String, String> body) {
        productImageService.deleteOwned(body == null ? null : body.get("url"), sellerId);
        return Result.success(null);
    }

    @GetMapping("/{filename:.+}")
    public ResponseEntity<Resource> image(@PathVariable String filename) {
        Resource resource = productImageService.load(filename);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic())
                .contentType(MediaType.parseMediaType(productImageService.contentType(filename)))
                .body(resource);
    }
}
