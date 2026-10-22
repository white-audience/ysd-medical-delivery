package com.ysd;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class YsdReportServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(YsdReportServiceApplication.class, args);
    }
}
