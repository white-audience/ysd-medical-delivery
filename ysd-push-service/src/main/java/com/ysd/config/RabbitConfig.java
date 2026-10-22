package com.ysd.config;

import org.springframework.amqp.core.AnonymousQueue;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MQ 配置：fanout 交换机 + 每实例独立匿名队列。
 *
 * <p>用 {@link AnonymousQueue} 保证每个 push-service 实例启动时各声明一个**唯一**队列
 * （exclusive、auto-delete），绑定到同一个 fanout 交换机 → 每个实例都能收到全量消息
 * （这是"多实例各自推送"的关键；若用固定队列名则变成竞争消费，只有一个实例收到）。
 */
@Configuration
public class RabbitConfig {

    /** 跨实例广播交换机（对齐原 Redis 频道 ws:broadcast 的语义） */
    public static final String WS_BROADCAST_EXCHANGE = "ws.broadcast";

    @Bean
    public FanoutExchange wsBroadcastExchange() {
        return new FanoutExchange(WS_BROADCAST_EXCHANGE, true, false);
    }

    @Bean
    public Queue wsPushQueue() {
        return new AnonymousQueue();
    }

    @Bean
    public Binding wsPushBinding(FanoutExchange wsBroadcastExchange, Queue wsPushQueue) {
        return BindingBuilder.bind(wsPushQueue).to(wsBroadcastExchange);
    }
}
