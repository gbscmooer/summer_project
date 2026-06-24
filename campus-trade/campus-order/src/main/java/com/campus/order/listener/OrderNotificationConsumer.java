package com.campus.order.listener;

import com.campus.order.config.RabbitMQConfig;
import com.campus.order.dto.OrderNotifyMessage;
import com.campus.order.entity.Notification;
import com.campus.order.mapper.NotificationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 订单相关的 MQ 消费者。
 *
 * <p>监听 {@code order.notify.queue}，收到消息后：
 * <ol>
 *   <li>打印日志（便于演示和调试）</li>
 *   <li>将通知写入 {@code t_notification} 表，前端可查询展示</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderNotificationConsumer {

    private final NotificationMapper notificationMapper;

    @RabbitListener(queues = RabbitMQConfig.ORDER_NOTIFY_QUEUE)
    public void processOrderNotification(OrderNotifyMessage message) {
        log.info("====== [RabbitMQ 消费者] 收到下单通知 ======");
        log.info("订单号: {}", message.getOrderNo());
        log.info("商品标题: {}", message.getProductTitle());
        log.info("卖家 ID: {}", message.getSellerId());
        log.info("买家 ID: {}", message.getBuyerId());

        // 写入通知表，发给卖家
        Notification notification = new Notification();
        notification.setUserId(message.getSellerId());
        notification.setType("ORDER_CREATED");
        notification.setTitle("你的商品被拍下啦！");
        notification.setContent(
                String.format("买家已下单「%s」，订单号 %s，成交价 ¥%s",
                        message.getProductTitle(),
                        message.getOrderNo(),
                        message.getPrice()));
        notification.setOrderNo(message.getOrderNo());
        notification.setIsRead(0);
        notification.setCreateTime(LocalDateTime.now());
        notificationMapper.insert(notification);

        log.info("====== 通知已写入数据库（卖家 {} 将收到通知）======", message.getSellerId());
    }
}
