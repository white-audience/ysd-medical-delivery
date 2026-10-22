package com.ysd;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 推送服务：只做 WebSocket。接收 {@code ws.broadcast} 交换机（fanout）的消息，
 * 推给本实例的客户端。无业务库、无定时任务、不调用其他服务。
 * 端口 8086，注册名 ysd-push-service。
 */
@SpringBootApplication
@EnableDiscoveryClient
@Slf4j
public class YsdPushServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(YsdPushServiceApplication.class, args);
        log.info("push service started");
    }
}
