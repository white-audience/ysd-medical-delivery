package com.ysd;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class YsdAdminServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(YsdAdminServiceApplication.class, args);
    }
}
