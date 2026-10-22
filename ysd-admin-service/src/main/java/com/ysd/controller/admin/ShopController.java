package com.ysd.controller.admin;

import com.ysd.result.Result;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;

@RestController("adminShopController")
@RequestMapping("/admin/shop")
@Tag(name = "店铺相关接口")
@Slf4j
public class ShopController  {

    public static final String KEY = "SHOP_STATUS";
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 设置营业状态
     * @param status
     * @return
     */
    @PutMapping("/{status}")
    @Operation(summary = "设置营业状态")
    public Result setStatus(@PathVariable Integer status){
    log.info("设置营业状态：{}", status==1?"营业中":"打烊中");
    stringRedisTemplate.opsForValue().set(KEY, String.valueOf(status));
    return Result.success();
    }
    @GetMapping("/status")
    @Operation(summary = "查询营业状态")
    public Result<Integer> getStatus(){
        String value = stringRedisTemplate.opsForValue().get(KEY);
        Integer status = value == null ? null : Integer.valueOf(value);
        log.info("查询营业状态为：{}", status == null ? "未设置" : (Objects.equals(status, 1) ? "营业中" : "打烊中"));
        return Result.success(status);
    }

}
