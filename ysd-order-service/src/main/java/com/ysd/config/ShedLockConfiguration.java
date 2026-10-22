package com.ysd.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * ShedLock 分布式锁配置（Redis 存储，复用 application.yml 的 db10）。
 *
 * <p>配合 {@code @EnableSchedulerLock}（在 {@link com.ysd.YsdOrderServiceApplication} 上）
 * 与各 {@code @SchedulerLock} 任务使用，保证多实例部署时同一时刻只有一个实例执行定时任务。
 */
@Configuration
public class ShedLockConfiguration {

    @Bean
    public LockProvider lockProvider(RedisConnectionFactory connectionFactory) {
        // 环境名 "ysd-order" 作为 Redis key 前缀的一部分，隔离不同服务的锁
        return new RedisLockProvider(connectionFactory, "ysd-order");
    }
}
