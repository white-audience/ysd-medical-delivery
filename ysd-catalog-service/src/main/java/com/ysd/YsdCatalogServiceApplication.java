package com.ysd;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 药品目录服务：分类（category）+ 药品（drug）+ 药品组合（drugCombo）。
 * 端口 8084，注册名 ysd-catalog-service。
 */
@SpringBootApplication
@EnableTransactionManagement
@EnableDiscoveryClient
@Slf4j
public class YsdCatalogServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(YsdCatalogServiceApplication.class, args);
        log.info("catalog service started");
    }
}
