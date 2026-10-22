package com.ysd.support;

import lombok.extern.slf4j.Slf4j;
import redis.embedded.RedisServer;

import java.io.IOException;

/**
 * 测试用真实 Redis 进程。
 *
 * <p>为什么不用 Mockito mock：本项目的并发测试要验证「原子性」与「互斥」，
 * mock 掉 {@code RedisTemplate} 之后所有操作都变成单线程方法调用，
 * 无法暴露竞态，结论不可信。因此并发测试一律针对真实 redis-server 进程。
 *
 * <p>依赖 {@code com.github.codemonstur:embedded-redis}，该 jar 内置各平台 redis 二进制，
 * Windows 使用 {@code redis-server-5.0.14.1-windows-amd64.exe}。
 *
 * <p>生命周期：由 {@link EmbeddedRedisInitializer} 在 Spring 上下文创建前调用 {@link #start()}，
 * JVM 退出时通过 shutdown hook 关闭。同一 JVM 内重复调用是幂等的。
 */
@Slf4j
public final class EmbeddedRedisServer {

    /** 固定端口，避免随机端口无法写进 application-test.yml */
    public static final int PORT = 16379;

    /** 与 application-test.yml 中 spring.redis.password 保持一致 */
    public static final String PASSWORD = "test123";

    private static RedisServer server;
    private static boolean started;

    private EmbeddedRedisServer() {
    }

    public static synchronized void start() {
        if (started) {
            return;
        }
        try {
            server = RedisServer.newRedisServer()
                    .port(PORT)
                    .setting("bind 127.0.0.1")
                    .setting("requirepass " + PASSWORD)
                    .setting("maxmemory 64M")
                    .onShutdownForceStop(true)
                    .build();
            server.start();
            started = true;
            Runtime.getRuntime().addShutdownHook(new Thread(EmbeddedRedisServer::stopQuietly, "embedded-redis-shutdown"));
            log.info("embedded redis 已启动: 127.0.0.1:{}", PORT);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "embedded redis 启动失败（端口 " + PORT + "）。"
                            + "请检查该端口是否被占用，或改用本机 Redis / Docker Redis 并调整 application-test.yml。", e);
        }
    }

    public static synchronized void stopQuietly() {
        if (server != null) {
            try {
                server.stop();
            } catch (IOException e) {
                log.warn("embedded redis 停止失败", e);
            }
            server = null;
            started = false;
        }
    }
}
