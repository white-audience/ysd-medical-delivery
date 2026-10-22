package com.ysd.cache;

import com.ysd.dto.DrugDTO;
import com.ysd.entity.Drug;
import com.ysd.mapper.DrugSpecMapper;
import com.ysd.mapper.DrugMapper;
import com.ysd.service.DrugService;
import com.ysd.support.AbstractSpringBootTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证阶段 5.4 的并发相关能力：本地互斥锁防击穿、延迟双删。
 *
 * <p>并发测试针对真实 embedded Redis（mock 掉 RedisTemplate 会串行化操作，暴露不出竞态）。
 * 延迟双删用「确定性」写法验证：不玩读写线程竞速，避免 flaky。
 */
@DisplayName("catalog 缓存：本地锁防击穿 / 延迟双删")
class CatalogCacheConcurrencyTest extends AbstractSpringBootTest {

    private static final String DRUG_KEY = "ysd:catalog:drug:1";

    @MockitoBean
    private DrugMapper drugMapper;
    @MockitoBean
    private DrugSpecMapper specMapper;

    @Autowired
    private DrugService drugService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @BeforeEach
    void cleanCache() {
        redisTemplate.execute((RedisCallback<Object>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
    }

    @Test
    @DisplayName("T5 热点 key 失效瞬间 50 并发查询，DB 只被回源一次（本地锁）")
    void 并发查同key_只回源一次() throws Exception {
        AtomicInteger dbCount = new AtomicInteger();
        when(drugMapper.list(any())).thenAnswer(invocation -> {
            dbCount.incrementAndGet();
            Thread.sleep(80);   // 放大未命中窗口，让并发请求真正重叠
            return List.of(Drug.builder().id(1L).name("测试药品A").categoryId(1L)
                    .price(new BigDecimal("12.00")).status(1).build());
        });
        // 与生产一致：MyBatis 返回 ArrayList；Collections$EmptyList 反序列化必崩
        when(specMapper.getByDrugId(any())).thenReturn(new ArrayList<>());

        int threads = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        try {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        drugService.listWithSpecs(Drug.builder().categoryId(1L).status(1).build());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(30, TimeUnit.SECONDS)).as("50 个请求应在 30s 内结束").isTrue();
        } finally {
            pool.shutdown();
            pool.awaitTermination(10, TimeUnit.SECONDS);
        }

        assertThat(dbCount.get()).as("50 并发应只回源 1 次").isEqualTo(1);
        verify(drugMapper, times(1)).list(any());
    }

    @Test
    @DisplayName("T6 更新后：立即删一次 + 500ms 后延时删一次（清掉晚到的旧值回写）")
    void 延迟双删清掉晚到的旧值回写() throws Exception {
        // 1) warm 缓存
        when(drugMapper.list(any())).thenReturn(List.of(Drug.builder().id(1L).name("旧菜").categoryId(1L)
                .price(new BigDecimal("12.00")).status(1).build()));
        // 与生产一致：MyBatis 返回 ArrayList；Collections$EmptyList 反序列化必崩
        when(specMapper.getByDrugId(any())).thenReturn(new ArrayList<>());
        drugService.listWithSpecs(Drug.builder().categoryId(1L).status(1).build());
        assertThat(redisTemplate.hasKey(DRUG_KEY)).as("初次查询应写入缓存").isTrue();

        // 2) 更新：第一次删除（立即）
        DrugDTO dto = new DrugDTO();
        dto.setId(1L);
        dto.setName("改过的菜");
        dto.setCategoryId(1L);
        dto.setPrice(new BigDecimal("9.99"));
        dto.setSpecs(Collections.emptyList());
        drugService.updateWithSpecs(dto);
        assertThat(redisTemplate.hasKey(DRUG_KEY)).as("更新后应立刻删除缓存").isFalse();

        // 3) 模拟窗口期内"读请求把旧值回写进缓存"
        redisTemplate.opsForValue().set(DRUG_KEY, new ArrayList<>(List.of(
                Drug.builder().id(1L).name("晚到的旧值").categoryId(1L).status(1).build())));
        assertThat(redisTemplate.hasKey(DRUG_KEY)).isTrue();

        // 4) 等延时任务执行（500ms + 余量）
        Thread.sleep(700);
        assertThat(redisTemplate.hasKey(DRUG_KEY)).as("延时双删的第二次删除应清掉旧值回写").isFalse();
    }
}
