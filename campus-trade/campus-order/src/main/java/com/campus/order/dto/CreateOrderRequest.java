package com.campus.order.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 下单请求体。买家 ID 从请求头 {@code X-User-Id} 取，故 body 只需商品 ID。
 */
@Data
public class CreateOrderRequest {

    @NotNull(message = "商品ID不能为空")
    private Long productId;
}
