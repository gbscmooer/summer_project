package com.campus.order.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String ORDER_EXCHANGE = "order.exchange";
    public static final String ORDER_NOTIFY_QUEUE = "order.notify.queue";
    public static final String ORDER_NOTIFY_ROUTING_KEY = "order.notify";

    public static final String ORDER_NOTIFY_DLX = "order.notify.dlx";
    public static final String ORDER_NOTIFY_DLQ = "order.notify.dlq";
    public static final String ORDER_NOTIFY_DLQ_ROUTING_KEY = "order.notify.dlq";

    // 交换机
    @Bean
    public TopicExchange orderExchange() {
        return new TopicExchange(ORDER_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange orderNotifyDeadLetterExchange() {
        return new TopicExchange(ORDER_NOTIFY_DLX, true, false);
    }

    // 队列
    @Bean
    public Queue orderNotifyQueue() {
        return QueueBuilder.durable(ORDER_NOTIFY_QUEUE)
                .withArgument("x-dead-letter-exchange", ORDER_NOTIFY_DLX)
                .withArgument("x-dead-letter-routing-key", ORDER_NOTIFY_DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue orderNotifyDeadLetterQueue() {
        return new Queue(ORDER_NOTIFY_DLQ, true);
    }

    // 绑定关系
    @Bean
    public Binding orderNotifyBinding() {
        return BindingBuilder.bind(orderNotifyQueue()).to(orderExchange()).with(ORDER_NOTIFY_ROUTING_KEY);
    }

    @Bean
    public Binding orderNotifyDeadLetterBinding() {
        return BindingBuilder.bind(orderNotifyDeadLetterQueue())
                .to(orderNotifyDeadLetterExchange())
                .with(ORDER_NOTIFY_DLQ_ROUTING_KEY);
    }

    // --- Sec-Kill Constants ---
    public static final String SECKILL_EXCHANGE = "seckill.exchange";
    public static final String SECKILL_QUEUE = "seckill.queue";
    public static final String SECKILL_ROUTING_KEY = "seckill.order";

    public static final String SECKILL_DLX = "seckill.dlx";
    public static final String SECKILL_DLQ = "seckill.dlq";
    public static final String SECKILL_DLQ_ROUTING_KEY = "seckill.dlq";

    @Bean
    public TopicExchange seckillExchange() {
        return new TopicExchange(SECKILL_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange seckillDeadLetterExchange() {
        return new TopicExchange(SECKILL_DLX, true, false);
    }

    @Bean
    public Queue seckillQueue() {
        return QueueBuilder.durable(SECKILL_QUEUE)
                .withArgument("x-dead-letter-exchange", SECKILL_DLX)
                .withArgument("x-dead-letter-routing-key", SECKILL_DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue seckillDeadLetterQueue() {
        return new Queue(SECKILL_DLQ, true);
    }

    @Bean
    public Binding seckillBinding() {
        return BindingBuilder.bind(seckillQueue()).to(seckillExchange()).with(SECKILL_ROUTING_KEY);
    }

    @Bean
    public Binding seckillDeadLetterBinding() {
        return BindingBuilder.bind(seckillDeadLetterQueue())
                .to(seckillDeadLetterExchange())
                .with(SECKILL_DLQ_ROUTING_KEY);
    }

    // 消息序列化器，使用 JSON 格式
    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
