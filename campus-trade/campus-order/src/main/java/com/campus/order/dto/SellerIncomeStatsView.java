package com.campus.order.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class SellerIncomeStatsView {
    /** 累计成交额（已付款 + 已完成） */
    private BigDecimal totalRevenue;
    private long completedOrders;
    private long pendingPaymentOrders;
    private long pendingShipmentOrders;
    private long cancelledOrders;
    private long totalOrders;
}
