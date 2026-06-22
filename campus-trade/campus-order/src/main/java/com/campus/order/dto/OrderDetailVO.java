package com.campus.order.dto;

import com.campus.order.entity.Order;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单详情返回体（含买卖家昵称、状态文案）。
 */
@Data
public class OrderDetailVO {

    private Long orderId;
    private String orderNo;
    private Long productId;
    private String productTitle;
    private BigDecimal price;
    private Long buyerId;
    private String buyerNickname;
    private Long sellerId;
    private String sellerNickname;
    private Integer status;
    private String statusText;
    private LocalDateTime createTime;

    /** 由订单实体构造基础字段，昵称与状态文案由 service 补齐。 */
    public static OrderDetailVO from(Order order) {
        OrderDetailVO vo = new OrderDetailVO();
        vo.setOrderId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setProductId(order.getProductId());
        vo.setProductTitle(order.getProductTitle());
        vo.setPrice(order.getPrice());
        vo.setBuyerId(order.getBuyerId());
        vo.setSellerId(order.getSellerId());
        vo.setStatus(order.getStatus());
        vo.setCreateTime(order.getCreateTime());
        return vo;
    }
}
