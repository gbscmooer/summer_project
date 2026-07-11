package com.campus.product.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ProductImageService {
    List<String> store(List<MultipartFile> files, Long uploaderId);
    Resource load(String filename);
    String contentType(String filename);
    /** 仅允许读取本人上传的站内图片，供 AI 草稿使用 */
    List<String> readAsDataUrls(List<String> imageUrls, Long requesterId);
    void assertOwned(List<String> imageUrls, Long requesterId);
    void deleteOwned(String imageUrl, Long uploaderId);
}
