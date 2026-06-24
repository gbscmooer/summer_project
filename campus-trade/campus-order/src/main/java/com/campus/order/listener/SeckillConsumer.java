package com.campus.order.listener;

import com.campus.order.config.RabbitMQConfig;
import com.campus.order.dto.SeckillMessage;
import com.campus.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RabbitListener(queues = RabbitMQConfig.SECKILL_QUEUE)
@RequiredArgsConstructor
public class SeckillConsumer {

    private final OrderService orderService;
    private final RedisTemplate<String, Object> redisTemplate;

    @RabbitHandler
    public void handleMessage(SeckillMessage message) {
        log.info("开始处理秒杀异步订单创建: {}", message);
        Long productId = message.getProductId();
        Long buyerId = message.getBuyerId();
        String resultKey = "seckill:result:" + productId + ":" + buyerId;

        try {
            String orderNo = orderService.createSeckillOrder(buyerId, productId);
            // 写入成功结果
            redisTemplate.opsForValue().set(resultKey, orderNo, java.time.Duration.ofMinutes(15));
            log.info("秒杀订单创建成功: user={}, orderNo={}", buyerId, orderNo);
        } catch (Exception e) {
            log.error("秒杀订单创建失败: user={}, product={}", buyerId, productId, e);
            // 写入失败标记
            redisTemplate.opsForValue().set(resultKey, "failed", java.time.Duration.ofMinutes(15));
            // 还原 Redis 中的库存预扣额
            String stockKey = "seckill:stock:" + productId;
            redisTemplate.opsForValue().increment(stockKey);
        }
    }
}
