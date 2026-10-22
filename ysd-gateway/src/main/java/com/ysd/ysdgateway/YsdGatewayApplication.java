package com.ysd.ysdgateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(scanBasePackages = {"com.ysd.ysdgateway", "com.ysd.filter", "com.ysd.properties"})
@EnableDiscoveryClient
public class YsdGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(YsdGatewayApplication.class, args);
    }

}
