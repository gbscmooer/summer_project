package com.campus.order.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单实体，对应 {@code t_order} 表。
 *
 * <p>下单时冗余商品标题、价格、卖家 ID，避免后续展示订单时还要回查商品服务，
 * 也保证商品被删改后订单仍能完整显示成交快照。
 */
@Data
@TableName("t_order")
public class Order {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 订单号（业务唯一，长度 ≤ 32），如 {@code 20260622153012001}。 */
    private String orderNo;

    /** Unique idempotency key carried by a seckill message; null for ordinary orders. */
    private String requestId;

    private Long productId;

    /** 冗余：下单时的商品标题（成交快照）。 */
    private String productTitle;

    /** 冗余：下单时的成交价。 */
    private BigDecimal price;

    private Long buyerId;

    /** 冗余：下单时的卖家 ID，用于"我卖出的"查询与权限校验。 */
    private Long sellerId;

    /** 订单状态：0 待付款 / 1 已付款 / 2 已完成 / 3 已取消。 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
