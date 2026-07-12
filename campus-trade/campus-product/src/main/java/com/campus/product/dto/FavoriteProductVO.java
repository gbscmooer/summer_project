package com.campus.product.dto;

import com.campus.product.entity.Product;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;

@Data
public class FavoriteProductVO {
    private Long productId;
    private String title;
    private String description;
    private BigDecimal price;
    private String cover;
    private String category;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime favoriteTime;

    public static FavoriteProductVO from(Product product, LocalDateTime favoriteTime) {
        FavoriteProductVO vo = new FavoriteProductVO();
        vo.setProductId(product.getId());
        vo.setTitle(product.getTitle());
        vo.setDescription(product.getDescription());
        vo.setPrice(product.getPrice());
        vo.setCategory(product.getCategory());
        vo.setStatus(product.getStatus());
        vo.setCreateTime(product.getCreateTime());
        vo.setFavoriteTime(favoriteTime);
        if (product.getImages() != null && !product.getImages().isBlank()) {
            vo.setCover(Arrays.stream(product.getImages().split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .findFirst()
                    .orElse(null));
        }
        return vo;
    }
}
