package com.campus.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.order.entity.Order;
import com.campus.order.mapper.OrderMapper;
import com.campus.order.service.OrderService;
import com.campus.order.service.OrderTimeoutService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 扫描超时未付订单并系统关单，模式对齐 {@link StockCompensationServiceImpl} 的定时批量重试。
 */
@Slf4j
@Service
public class OrderTimeoutServiceImpl implements OrderTimeoutService {

    private static final int STATUS_UNPAID = 0;

    private final OrderMapper orderMapper;
    private final OrderService orderService;

    @Value("${campus.order.unpaid-timeout-minutes:15}")
    private int unpaidTimeoutMinutes;

    @Value("${campus.order.timeout-batch-size:100}")
    private int timeoutBatchSize;

    public OrderTimeoutServiceImpl(OrderMapper orderMapper, OrderService orderService) {
        this.orderMapper = orderMapper;
        this.orderService = orderService;
    }

    @Override
    @Scheduled(fixedDelayString = "${campus.order.timeout-scan-delay-ms:60000}")
    public int closeExpiredUnpaidOrders() {
        int timeoutMinutes = Math.max(1, unpaidTimeoutMinutes);
        int batchSize = Math.min(500, Math.max(1, timeoutBatchSize));
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(timeoutMinutes);

        List<Order> candidates = orderMapper.selectList(
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getStatus, STATUS_UNPAID)
                        .lt(Order::getCreateTime, deadline)
                        .orderByAsc(Order::getId)
                        .last("LIMIT " + batchSize));

        int closed = 0;
        for (Order order : candidates) {
            try {
                if (orderService.closeUnpaidBySystem(order.getId())) {
                    closed++;
                }
            } catch (Exception e) {
                log.error("订单超时关单失败，orderId={}, orderNo={}", order.getId(), order.getOrderNo(), e);
            }
        }

        if (!candidates.isEmpty()) {
            log.info("订单超时自动关单扫描完成，候选={}, 关闭={}", candidates.size(), closed);
        }
        return closed;
    }
}
