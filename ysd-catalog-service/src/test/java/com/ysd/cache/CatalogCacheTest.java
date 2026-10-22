package com.ysd.cache;

import com.ysd.entity.Category;
import com.ysd.entity.Drug;
import com.ysd.entity.DrugCombo;
import com.ysd.mapper.CategoryMapper;
import com.ysd.mapper.DrugSpecMapper;
import com.ysd.mapper.DrugMapper;
import com.ysd.mapper.DrugComboMapper;
import com.ysd.service.CategoryService;
import com.ysd.service.DrugService;
import com.ysd.service.DrugComboService;
import com.ysd.support.AbstractSpringBootTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证阶段 5.4 的缓存基础能力：命中、空值防穿透、TTL 随机偏移、JSON 序列化。
 *
 * <p>用 {@code @MockBean} 替换 Mapper，把「DB 被查询的次数」变成可计数的 mock 调用次数。
 * 针对真实 embedded Redis，不用 Mockito 替换 RedisTemplate。
 */
@DisplayName("catalog 缓存：命中 / 空值防穿透 / TTL / JSON 序列化")
class CatalogCacheTest extends AbstractSpringBootTest {

    private static final String DRUG_KEY = "ysd:catalog:drug:1";
    private static final String COMBO_KEY = "ysd:catalog:combo:1";
    private static final String CATEGORY_KEY = "ysd:catalog:category:1";

    @MockitoBean
    private DrugMapper drugMapper;
    @MockitoBean
    private DrugSpecMapper specMapper;
    @MockitoBean
    private DrugComboMapper drugComboMapper;
    @MockitoBean
    private CategoryMapper categoryMapper;

    @Autowired
    private DrugService drugService;
    @Autowired
    private DrugComboService drugComboService;
    @Autowired
    private CategoryService categoryService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    void cleanCache() {
        redisTemplate.execute((RedisCallback<Object>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
    }

    private Drug drugInCategory1() {
        return Drug.builder().id(1L).name("测试药品A").categoryId(1L)
                .price(new BigDecimal("12.00")).status(1).build();
    }

    @Test
    @DisplayName("T1 同一分类连查两次，DB 只被查一次（第二次命中缓存）")
    void 命中缓存后不再查库() {
        when(drugMapper.list(any())).thenReturn(List.of(drugInCategory1()));
        // 用 ArrayList 而非 Collections.emptyList()：与 MyBatis 真实返回值一致。
        // Collections$EmptyList 是包私有、无默认构造的类，写进缓存后无法反序列化（design: 空集合一律 ArrayList）。
        when(specMapper.getByDrugId(any())).thenReturn(new ArrayList<>());

        Drug query = Drug.builder().categoryId(1L).status(1).build();
        drugService.listWithSpecs(query);
        drugService.listWithSpecs(query);

        verify(drugMapper, times(1)).list(any());
    }

    @Test
    @DisplayName("T2 查询不存在的分类连续 5 次，DB 只被查一次（空值缓存防穿透）")
    void 查不存在分类_只查库一次() {
        when(drugMapper.list(any())).thenReturn(Collections.emptyList());

        Drug query = Drug.builder().categoryId(9999L).status(1).build();
        for (int i = 0; i < 5; i++) {
            drugService.listWithSpecs(query);
        }

        verify(drugMapper, times(1)).list(any());

        String key = "ysd:catalog:drug:9999";
        assertThat(redisTemplate.hasKey(key)).as("空结果也应写入缓存").isTrue();
        Long ttl = redisTemplate.getExpire(key);
        assertThat(ttl).as("空值缓存 TTL 应在 60~70s").isNotNull()
                .isGreaterThanOrEqualTo(60L).isLessThanOrEqualTo(70L);
    }

    @Test
    @DisplayName("T3 各缓存 TTL 落在预期区间（含随机偏移）")
    void TTL落在预期区间() {
        when(drugMapper.list(any())).thenReturn(List.of(drugInCategory1()));
        // 用 ArrayList 而非 Collections.emptyList()：与 MyBatis 真实返回值一致。
        // Collections$EmptyList 是包私有、无默认构造的类，写进缓存后无法反序列化（design: 空集合一律 ArrayList）。
        when(specMapper.getByDrugId(any())).thenReturn(new ArrayList<>());
        // 直接进缓存的顶层值必须是可变 ArrayList：MyBatis 返回 ArrayList，
        // List.of() 会写成 java.util.ImmutableCollections$ListN（包私有、不可实例化），读回必炸
        when(drugComboMapper.list(any())).thenReturn(new ArrayList<>(List.of(
                DrugCombo.builder().id(1L).categoryId(1L).name("测试药品组合").status(1).build())));
        when(categoryMapper.list(any())).thenReturn(new ArrayList<>(List.of(
                Category.builder().id(1L).type(1).name("测试分类").status(1).build())));

        drugService.listWithSpecs(Drug.builder().categoryId(1L).status(1).build());
        drugComboService.list(DrugCombo.builder().categoryId(1L).status(1).build());
        categoryService.list(1);

        // 基础 TTL + [0, jitter]，读取时有 <1s 损耗 → 下界各留 2s 容差
        assertThat(redisTemplate.getExpire(DRUG_KEY)).as("药品 10~12min")
                .isBetween(598L, 720L);
        assertThat(redisTemplate.getExpire(COMBO_KEY)).as("药品组合 15~18min")
                .isBetween(898L, 1080L);
        assertThat(redisTemplate.getExpire(CATEGORY_KEY)).as("分类 30~35min")
                .isBetween(1798L, 2100L);
    }

    @Test
    @DisplayName("T4 缓存 value 是 JSON（含 @class），不是 JDK 二进制")
    void 缓存值是JSON且带类型信息() {
        when(drugMapper.list(any())).thenReturn(List.of(drugInCategory1()));
        // 用 ArrayList 而非 Collections.emptyList()：与 MyBatis 真实返回值一致。
        // Collections$EmptyList 是包私有、无默认构造的类，写进缓存后无法反序列化（design: 空集合一律 ArrayList）。
        when(specMapper.getByDrugId(any())).thenReturn(new ArrayList<>());

        drugService.listWithSpecs(Drug.builder().categoryId(1L).status(1).build());

        String raw = stringRedisTemplate.opsForValue().get(DRUG_KEY);
        assertThat(raw).as("value 应为可读 JSON 数组（顶层是 List）").isNotNull()
                .startsWith("[").contains("@class").contains("DrugVO");

        byte[] bytes = redisTemplate.execute((RedisCallback<byte[]>) connection ->
                connection.stringCommands().get(DRUG_KEY.getBytes(StandardCharsets.UTF_8)));
        assertThat(bytes).isNotNull().isNotEmpty();
        assertThat(bytes[0]).as("首字节应为 '['（0x5B），而非 JDK 序列化的 0xAC")
                .isEqualTo((byte) '[');
    }

    @Test
    @DisplayName("T7 三类缓存对象写入后立即读回，不抛反序列化异常（覆盖 DrugVO 的 BigDecimal / DrugCombo 的 BigDecimal / Category 无隐患字段）")
    void 缓存对象往返序列化全覆盖() {
        when(drugMapper.list(any())).thenReturn(List.of(drugInCategory1()));
        when(specMapper.getByDrugId(any())).thenReturn(new ArrayList<>());
        when(drugComboMapper.list(any())).thenReturn(new ArrayList<>(List.of(
                DrugCombo.builder().id(1L).categoryId(1L).name("测试药品组合").status(1)
                        .price(new BigDecimal("18.00")).build())));
        when(categoryMapper.list(any())).thenReturn(new ArrayList<>(List.of(
                Category.builder().id(1L).type(1).name("测试分类").status(1).build())));

        // 每次第二次调用都命中缓存 → 触发反序列化。任一类缺白名单或含不可实例化集合，这里就会抛异常。
        assertThatCode(() -> {
            drugService.listWithSpecs(Drug.builder().categoryId(1L).status(1).build());
            drugService.listWithSpecs(Drug.builder().categoryId(1L).status(1).build());
        }).as("DrugVO（含 BigDecimal price）往返").doesNotThrowAnyException();

        assertThatCode(() -> {
            drugComboService.list(DrugCombo.builder().categoryId(1L).status(1).build());
            drugComboService.list(DrugCombo.builder().categoryId(1L).status(1).build());
        }).as("DrugCombo（含 BigDecimal price）往返").doesNotThrowAnyException();

        assertThatCode(() -> {
            categoryService.list(1);
            categoryService.list(1);
        }).as("Category 往返").doesNotThrowAnyException();
    }
}
