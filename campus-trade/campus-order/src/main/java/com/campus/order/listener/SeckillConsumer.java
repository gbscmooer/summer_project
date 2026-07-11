package com.campus.order.listener;

import com.campus.order.config.RabbitMQConfig;
import com.campus.order.dto.SeckillMessage;
import com.campus.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RabbitListener(queues = RabbitMQConfig.SECKILL_QUEUE)
@RequiredArgsConstructor
public class SeckillConsumer {

    private final OrderService orderService;
    private final StringRedisTemplate stringRedisTemplate;

    @RabbitHandler
    public void handleMessage(SeckillMessage message) {
        log.info("开始处理秒杀异步订单创建: {}", message);
        Long productId = message.getProductId();
        Long buyerId = message.getBuyerId();
        String requestId = message.getRequestId();
        String resultKey = "seckill:result:" + productId + ":" + buyerId;

        String orderNo = null;
        try {
            orderNo = orderService.createSeckillOrder(requestId, buyerId, productId);
        } catch (Exception e) {
            log.error("秒杀订单创建失败: user={}, product={}", buyerId, productId, e);
            // 只有当 DB 创建订单失败时，才标记 failed 且回滚 Redis 库存
            try {
                stringRedisTemplate.opsForValue().set(resultKey, "failed", java.time.Duration.ofMinutes(15));
            } catch (Exception ex) {
                log.error("写入秒杀失败标记到 Redis 异常", ex);
            }
            try {
                String stockKey = "seckill:stock:" + productId;
                stringRedisTemplate.opsForValue().increment(stockKey);
            } catch (Exception ex) {
                log.error("回滚 Redis 库存异常", ex);
            }
            return;
        }

        // DB 订单创建成功，更新 Redis 结果
        try {
            stringRedisTemplate.opsForValue().set(resultKey, orderNo, java.time.Duration.ofMinutes(15));
            log.info("秒杀订单创建成功: user={}, orderNo={}", buyerId, orderNo);
        } catch (Exception e) {
            // DB 订单已经创建成功，即使 Redis 写入成功结果失败，也不应该标记 failed 或回滚库存！
            log.error("CRITICAL: 秒杀订单已在 DB 创建成功，但写入 Redis 成功结果失败: user={}, orderNo={}", buyerId, orderNo, e);
        }
    }
}
