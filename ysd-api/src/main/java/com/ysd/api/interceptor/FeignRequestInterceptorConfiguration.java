package com.ysd.api.interceptor;

import org.springframework.context.annotation.Bean;

/**
 * 只为 {@code @FeignClient(configuration = ...)} 提供出站拦截器。
 *
 * <p><b>故意不加 {@code @Configuration}</b>：消费方启动类在 {@code com.ysd} 包下，会扫描到
 * {@code com.ysd.api}；若加 {@code @Configuration} 会被当成全局 Feign 配置，对所有 FeignClient 生效。
 * 不加则仅在 @FeignClient 指定的子上下文中注册。
 */
public class FeignRequestInterceptorConfiguration {

    @Bean
    public FeignRequestInterceptor feignRequestInterceptor() {
        return new FeignRequestInterceptor();
    }
}
