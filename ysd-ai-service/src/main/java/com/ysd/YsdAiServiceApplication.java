package com.ysd;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * AI 服务：Agent 能力底座。端口 8088，注册名 ysd-ai-service。
 *
 * <p>启动类置于 {@code com.ysd} 根包，与其它业务服务一致，
 * 以便组件扫描覆盖 com.ysd.api.client / com.ysd.result / com.ysd.exception。
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.ysd.api.client")
@Slf4j
public class YsdAiServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(YsdAiServiceApplication.class, args);
        log.info("ai service started");
    }
}
