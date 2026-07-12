package com.campus.order.service;

/**
 * 未支付订单超时自动关单。
 */
public interface OrderTimeoutService {

    /**
     * 扫描超时未付订单并尝试系统关单。
     *
     * @return 本次成功关闭的订单数
     */
    int closeExpiredUnpaidOrders();
}
