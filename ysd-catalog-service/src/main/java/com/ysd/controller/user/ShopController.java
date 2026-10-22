package com.ysd.controller.user;

import com.ysd.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * 店铺营业状态查询（用户端，只读）。
 *
 * <p>数据来自 Redis db10 的 {@code SHOP_STATUS}，由管理端 {@code PUT /admin/shop/{status}} 写入。
 * 值以字符串 "0"/"1" 存储；对历史 JDK 序列化格式做一次性兼容读取。
 */
@RestController("userShopController")
@RequestMapping("/user/shop")
@Tag(name = "店铺相关接口")
@Slf4j
public class ShopController {

    public static final String KEY = "SHOP_STATUS";

    /** 未设置时的默认营业状态：0 打烊 */
    private static final Integer DEFAULT_STATUS = 0;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @GetMapping("/status")
    @Operation(summary = "查询营业状态")
    public Result<Integer> getStatus() {
        Integer status = readShopStatus();
        log.info("查询营业状态为：{}", Objects.equals(status, 1) ? "营业中" : "打烊中");
        return Result.success(status);
    }

    /**
     * 读取 SHOP_STATUS，兼容历史 JDK 序列化格式：
     * 非 "0"/"1" 时按旧格式取原始字节反序列化，成功后回写为字符串格式。
     */
    private Integer readShopStatus() {
        String value = stringRedisTemplate.opsForValue().get(KEY);
        if ("0".equals(value) || "1".equals(value)) {
            return Integer.valueOf(value);
        }
        if (value == null) {
            return DEFAULT_STATUS;
        }
        // 非 "0"/"1"：可能是旧 JDK 序列化 blob，取原始字节兼容反序列化
        try {
            byte[] raw = stringRedisTemplate.execute(
                    (RedisCallback<byte[]>) connection -> connection.get(KEY.getBytes(StandardCharsets.UTF_8)));
            if (raw != null) {
                Object legacy = new JdkSerializationRedisSerializer().deserialize(raw);
                if (legacy instanceof Integer legacyStatus) {
                    stringRedisTemplate.opsForValue().set(KEY, String.valueOf(legacyStatus));
                    log.info("SHOP_STATUS 旧格式已回写为字符串：{}", legacyStatus);
                    return legacyStatus;
                }
            }
        } catch (Exception e) {
            log.warn("SHOP_STATUS 兼容读取失败，按默认状态处理", e);
        }
        return DEFAULT_STATUS;
    }
}
