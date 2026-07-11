package com.campus.product.dto;

import com.campus.product.entity.Product;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;

@Data
public class ProductListVO {
    private Long productId;
    private String title;
    private String description;
    private BigDecimal price;
    private String cover;
    private String category;
    private Integer status;
    private LocalDateTime createTime;

    public static ProductListVO from(Product p) {
        ProductListVO vo = new ProductListVO();
        vo.setProductId(p.getId());
        vo.setTitle(p.getTitle());
        vo.setDescription(p.getDescription());
        vo.setPrice(p.getPrice());
        vo.setCategory(p.getCategory());
        vo.setStatus(p.getStatus());
        vo.setCreateTime(p.getCreateTime());
        if (p.getImages() != null && !p.getImages().isBlank()) {
            vo.setCover(Arrays.stream(p.getImages().split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .findFirst()
                    .orElse(null));
        }
        return vo;
    }
}
