package com.campus.order.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * MQ 消息体：下单通知。
 *
 * <p>使用简单 POJO 而非 Order 实体，避免 LocalDateTime 等类型在 JSON 序列化/反序列化时
 * 因缺少 JSR-310 模块而失败，也避免把数据库主键、状态等无关字段暴露给消费者。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderNotifyMessage implements Serializable {
    /** 订单号。 */
    private String orderNo;
    /** 商品 ID。 */
    private Long productId;
    /** 商品标题。 */
    private String productTitle;
    /** 成交积分。 */
    private BigDecimal price;
    /** 买家 ID。 */
    private Long buyerId;
    /** 卖家 ID（通知接收人）。 */
    private Long sellerId;
}
