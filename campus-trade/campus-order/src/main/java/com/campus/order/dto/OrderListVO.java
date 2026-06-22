package com.campus.order.dto;

import com.campus.order.entity.Order;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单列表项（精简）。
 *
 * <p>{@code counterpartNickname} 为"对方"昵称：买家视角是卖家，卖家视角是买家，
 * 由 service 按视角批量补齐，避免列表逐条调用用户服务。
 */
@Data
public class OrderListVO {

    private Long orderId;
    private String orderNo;
    private Long productId;
    private String productTitle;
    private BigDecimal price;
    private Integer status;
    private String statusText;
    private LocalDateTime createTime;
    /** 对方昵称（买家视角=卖家，卖家视角=买家）。 */
    private String counterpartNickname;

    public static OrderListVO from(Order order) {
        OrderListVO vo = new OrderListVO();
        vo.setOrderId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setProductId(order.getProductId());
        vo.setProductTitle(order.getProductTitle());
        vo.setPrice(order.getPrice());
        vo.setStatus(order.getStatus());
        vo.setCreateTime(order.getCreateTime());
        return vo;
    }
}
