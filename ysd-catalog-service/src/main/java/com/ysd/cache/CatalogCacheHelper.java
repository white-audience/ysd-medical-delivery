package com.ysd.cache;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * catalog 服务缓存读写封装。
 * key 前缀统一为 {@code ysd:catalog:}；失效一律走 SCAN，不用 KEYS。
 */
@Component
@Slf4j
public class CatalogCacheHelper {

    /** 药品缓存 key 前缀（按分类维度） */
    public static final String DRUG_PATTERN = "ysd:catalog:drug:*";
    /** 药品组合缓存 key 前缀（按分类维度） */
    public static final String COMBO_PATTERN = "ysd:catalog:combo:*";
    /** 分类缓存 key 前缀（按 type 维度） */
    public static final String CATEGORY_PATTERN = "ysd:catalog:category:*";

    public static String drugKey(Long categoryId) {
        return "ysd:catalog:drug:" + categoryId;
    }

    public static String comboKey(Long categoryId) {
        return "ysd:catalog:combo:" + categoryId;
    }

    public static String categoryKey(Integer type) {
        return "ysd:catalog:category:" + type;
    }

    /** 缓存类型 → 决定回源后写缓存用哪套 TTL 策略 */
    public enum CacheType { DRUG, COMBO, CATEGORY }

    private final RedisTemplate<String, Object> redisTemplate;
    // 按 key 维护本地互斥锁（防击穿）。key 空间有界（分类/type 维度），故不清理。
    // TODO 若 key 空间变为无界（如按 userId），需改为引用计数或 Guava Striped 锁
    private final ConcurrentHashMap<String, Lock> locks = new ConcurrentHashMap<>();

    // 延迟双删用的调度器：daemon 线程，写操作不频繁，2 个线程够用
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(
            2,
            r -> {
                Thread t = new Thread(r, "cache-delayed-evict");
                t.setDaemon(true);
                return t;
            });

    @PreDestroy
    public void shutdownScheduler() {
        scheduler.shutdown();
    }

    public CatalogCacheHelper(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 读取缓存。返回类型由调用方自行保证与写入时一致（内部做了 unchecked 转换）。
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) redisTemplate.opsForValue().get(key);
    }

    /**
     * 带本地互斥锁的读缓存（防击穿）：miss → 按 key 加锁 → 锁内二次查 → 回源 → 写缓存。
     * 本地锁仅在本 JVM 内串行化；多实例部署需升级为分布式锁（TODO）。
     */
    public <T> T getWithLock(String key, CacheType type, Supplier<T> loader) {
        T cached = get(key);
        if (cached != null) {
            return cached;
        }
        Lock lock = lockFor(key);
        lock.lock();
        try {
            cached = get(key);
            if (cached != null) {
                return cached;
            }
            T value = loader.get();
            put(key, type, value);
            return value;
        } finally {
            lock.unlock();
        }
    }

    private Lock lockFor(String key) {
        return locks.computeIfAbsent(key, k -> new ReentrantLock());
    }

    private void put(String key, CacheType type, Object value) {
        if (value == null) {
            setEmpty(key);
            return;
        }
        if (value instanceof Collection<?> c && c.isEmpty()) {
            setEmpty(key);
            return;
        }
        switch (type) {
            case DRUG -> setDrug(key, value);
            case COMBO -> setDrugCombo(key, value);
            case CATEGORY -> setCategory(key, value);
        }
    }

    // TTL 策略：基础时长 + 随机偏移（防雪崩），偏移取 [0, jitterSeconds] 闭区间
    private static final Duration DRUG_BASE_TTL = Duration.ofMinutes(10);
    private static final int DRUG_JITTER_SECONDS = 120;
    private static final Duration COMBO_BASE_TTL = Duration.ofMinutes(15);
    private static final int COMBO_JITTER_SECONDS = 180;
    private static final Duration CATEGORY_BASE_TTL = Duration.ofMinutes(30);
    private static final int CATEGORY_JITTER_SECONDS = 300;
    // 空值缓存（防穿透）用短 TTL
    private static final Duration EMPTY_BASE_TTL = Duration.ofSeconds(60);
    private static final int EMPTY_JITTER_SECONDS = 10;

    /** 写药品缓存：10min + [0,2min]。 */
    public void setDrug(String key, Object value) {
        setWithJitter(key, value, DRUG_BASE_TTL, DRUG_JITTER_SECONDS);
    }

    /** 写药品组合缓存：15min + [0,3min]。 */
    public void setDrugCombo(String key, Object value) {
        setWithJitter(key, value, COMBO_BASE_TTL, COMBO_JITTER_SECONDS);
    }

    /** 写分类缓存：30min + [0,5min]。 */
    public void setCategory(String key, Object value) {
        setWithJitter(key, value, CATEGORY_BASE_TTL, CATEGORY_JITTER_SECONDS);
    }

    /**
     * 写空值缓存（防穿透）：空集合 + 短 TTL 60~70s。
     * 存 new ArrayList 而非 Collections.emptyList()——后者在 default typing 下会写成
     * java.util.Collections$EmptyList，包私有且无默认构造，反序列化会失败。
     */
    public void setEmpty(String key) {
        setWithJitter(key, new ArrayList<>(), EMPTY_BASE_TTL, EMPTY_JITTER_SECONDS);
    }

    private void setWithJitter(String key, Object value, Duration baseTtl, int jitterSeconds) {
        long ttlSeconds = baseTtl.getSeconds() + ThreadLocalRandom.current().nextLong(jitterSeconds + 1);
        redisTemplate.opsForValue().set(key, value, Duration.ofSeconds(ttlSeconds));
    }

    /** 删除单个 key。 */
    public void evict(String key) {
        redisTemplate.delete(key);
    }

    /** 按前缀批量删除（SCAN，非阻塞）。 */
    public void evictByPattern(String pattern) {
        Set<String> keys = new HashSet<>();
        try (Cursor<String> cursor = redisTemplate.scan(
                ScanOptions.scanOptions().match(pattern).count(200).build())) {
            cursor.forEachRemaining(keys::add);
        }
        if (!keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    /**
     * 延迟双删的第二次删除：事务提交后延时 delayMs 再清一次缓存，
     * 清掉窗口期内"读请求把旧值回写进缓存"的脏数据。
     */
    public void scheduleDelayedEvict(String pattern, long delayMs) {
        Runnable task = () -> {
            try {
                evictByPattern(pattern);
            } catch (Exception e) {
                log.warn("延迟删除缓存失败，pattern={}", pattern, e);   // 记 WARN，不重试
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    scheduler.schedule(task, delayMs, TimeUnit.MILLISECONDS);
                }
            });
        } else {
            // 无事务上下文（如直连调用）时退化为立即排期
            scheduler.schedule(task, delayMs, TimeUnit.MILLISECONDS);
        }
    }
}
