package com.ysd.mq;

import com.ysd.websocket.WebSocketServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * MQ 消费者：订阅本实例的匿名队列（绑定在 {@code ws.broadcast} fanout 交换机上），
 * 收到消息后推给本实例的客户端。
 *
 * <p>消费语义：自动 ack（默认）、不重试、不建 DLQ —— 广播类消息重复推送无害。
 */
@Component
@Slf4j
public class WsPushListener {

    private final WebSocketServer webSocketServer;

    public WsPushListener(WebSocketServer webSocketServer) {
        this.webSocketServer = webSocketServer;
    }

    @RabbitListener(queues = "#{wsPushQueue.name}")
    public void onMessage(String message) {
        log.info("收到广播消息，推送给本实例客户端：{}", message);
        webSocketServer.sendToLocalClients(message);
    }
}
