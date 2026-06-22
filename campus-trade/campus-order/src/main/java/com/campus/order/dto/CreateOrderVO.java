package com.campus.order.dto;

import com.campus.order.entity.Order;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 下单成功返回体。
 */
@Data
public class CreateOrderVO {

    private Long orderId;
    private String orderNo;
    private Long productId;
    private String productTitle;
    private BigDecimal price;
    private Integer status;

    public static CreateOrderVO from(Order order) {
        CreateOrderVO vo = new CreateOrderVO();
        vo.setOrderId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setProductId(order.getProductId());
        vo.setProductTitle(order.getProductTitle());
        vo.setPrice(order.getPrice());
        vo.setStatus(order.getStatus());
        return vo;
    }
}
