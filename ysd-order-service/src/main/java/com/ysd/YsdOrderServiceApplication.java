package com.ysd;

import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 订单服务：订单（orders）+ 订单明细（order_detail）+ 购物车（shopping_cart）
 * + WebSocket（来单提醒 / 客户催单）+ 定时任务（订单超时 / 派送超时）。
 * 端口 8085，注册名 ysd-order-service。
 *
 * <p>阶段 5：下单 / 加购时对 user-service、catalog-service 的读取改为 Feign 远程调用
 * （{@code com.ysd.api.client}），不再直连它们的库表。
 */
@SpringBootApplication
@EnableTransactionManagement
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.ysd.api.client")
@Slf4j
public class YsdOrderServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(YsdOrderServiceApplication.class, args);
        log.info("order service started");
    }
}
