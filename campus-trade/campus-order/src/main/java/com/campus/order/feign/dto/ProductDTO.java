package com.campus.order.feign.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品信息 DTO（订单服务侧自定义，仅取下单所需字段，避免与商品服务强耦合）。
 * 对应 campus-product 的 {@code GET /product/inner/{id}} 返回结构。
 */
@Data
public class ProductDTO {
    private Long productId;
    private String title;
    private BigDecimal price;
    private Long sellerId;
    private Integer status;
    private Integer stock;
}
