package com.ysd.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 服务（阶段 5 从 ysd-order-service 迁入）。
 *
 * <p>会话 {@code sessionMap} 只保存**本实例**的连接；跨实例由 MQ 广播实现——
 * order-service 把消息发到 {@code ws.broadcast}（fanout）交换机，每个 push-service 实例
 * 各有独立队列，收到后调 {@link #sendToLocalClients} 推给本实例客户端。
 *
 * <p>注意：{@code @ServerEndpoint} 的连接实例由 WebSocket 容器（Tomcat）创建、**不是 Spring bean**，
 * 但连接实例只操作静态 {@link #sessionMap}，从不调用 {@link #sendToLocalClients}；
 * 后者由 Spring 单例 {@code WsPushListener} 调用，无需注入。
 */
@Component
@Slf4j
@ServerEndpoint("/ws/{sid}")
public class WebSocketServer {

    // 存放本实例的会话对象（连接线程写入、MQ 消费线程读取，用并发 Map）
    private static final Map<String, Session> sessionMap = new ConcurrentHashMap<>();

    /**
     * 连接建立成功调用的方法
     */
    @OnOpen
    public void onOpen(Session session, @PathParam("sid") String sid) {
        log.info("客户端：{} 建立连接", sid);
        sessionMap.put(sid, session);
    }

    /**
     * 收到客户端消息后调用的方法
     *
     * @param message 客户端发送过来的消息
     */
    @OnMessage
    public void onMessage(String message, @PathParam("sid") String sid) {
        log.info("收到来自客户端：{} 的信息:{}", sid, message);
    }

    /**
     * 连接关闭调用的方法
     */
    @OnClose
    public void onClose(@PathParam("sid") String sid) {
        log.info("连接断开:{}", sid);
        sessionMap.remove(sid);
    }

    /**
     * 仅推送本实例的客户端，供 MQ 消费者调用。
     *
     * @param message
     */
    public void sendToLocalClients(String message) {
        for (Session session : sessionMap.values()) {
            try {
                // 服务器向客户端发送消息
                session.getBasicRemote().sendText(message);
            } catch (Exception e) {
                log.error("WebSocket 推送失败", e);
            }
        }
    }
}
