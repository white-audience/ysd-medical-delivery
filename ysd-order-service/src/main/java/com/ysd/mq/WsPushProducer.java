package com.ysd.mq;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 推送消息生产端：把「来单提醒 / 客户催单」的 JSON 字符串发到 {@code ws.broadcast}
 * fanout 交换机，由各 ysd-push-service 实例消费后推送给自己的 WebSocket 客户端。
 *
 * <p>消息体格式与原 Redis Pub/Sub 完全一致（{@code {"type":1|2,"orderId":..,"content":".."}}），
 * 两端均以 String 收发，不需要 JSON 转换器。
 */
@Component
public class WsPushProducer {

    /** 与 ysd-push-service 的 RabbitConfig 保持一致 */
    public static final String WS_BROADCAST_EXCHANGE = "ws.broadcast";

    private final RabbitTemplate rabbitTemplate;

    public WsPushProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void push(String json) {
        rabbitTemplate.convertAndSend(WS_BROADCAST_EXCHANGE, "", json);
    }
}
