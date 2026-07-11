package com.campus.product.dto;

import com.campus.product.entity.Product;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Data
public class ProductDetailVO {
    private Long productId;
    private String title;
    private String description;
    private BigDecimal price;
    private List<String> images;
    private String category;
    private Long sellerId;
    private String sellerNickname;
    private Integer status;
    private Integer stock;
    private Integer viewCount;
    private LocalDateTime createTime;

    public static ProductDetailVO from(Product p) {
        ProductDetailVO vo = new ProductDetailVO();
        vo.setProductId(p.getId());
        vo.setTitle(p.getTitle());
        vo.setDescription(p.getDescription());
        vo.setPrice(p.getPrice());
        vo.setImages(p.getImages() != null
                ? new ArrayList<>(Arrays.stream(p.getImages().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList())
                : new ArrayList<>());
        vo.setCategory(p.getCategory());
        vo.setSellerId(p.getSellerId());
        vo.setStatus(p.getStatus());
        vo.setStock(p.getStock());
        vo.setViewCount(p.getViewCount());
        vo.setCreateTime(p.getCreateTime());
        return vo;
    }
}
