package com.ysd.support;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * 在 Spring 上下文创建之前拉起 embedded redis。
 *
 * <p>用 {@code ApplicationContextInitializer} 而不是静态代码块，是为了保证时序确定：
 * 静态代码块的初始化时机取决于类首次被使用的时刻，可能晚于上下文创建；
 * initializer 由 Spring 在 refresh 之前显式回调，一定早于任何 Redis 连接。
 */
public class EmbeddedRedisInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        EmbeddedRedisServer.start();
    }
}
