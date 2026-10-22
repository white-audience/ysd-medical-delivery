package com.ysd.api.client.fallback;

import com.ysd.api.client.UserClient;
import com.ysd.api.dto.AddressBookSnapshotDTO;
import com.ysd.exception.OrderBusinessException;
import com.ysd.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * UserClient 的熔断降级工厂：仅在「开启 Feign 熔断 + Sentinel 熔断触发」时被调用。
 *
 * <p>地址簿是下单的核心信息，**不得编造兜底数据**，因此这里直接抛业务异常（fail-fast），
 * 由消费方 order-service 的 GlobalExceptionHandler 统一转成 {@code Result.error}。
 */
@Slf4j
@Component
public class UserClientFallbackFactory implements FallbackFactory<UserClient> {

    @Override
    public UserClient create(Throwable cause) {
        log.warn("UserClient 触发熔断降级", cause);
        return id -> {
            throw new OrderBusinessException("系统繁忙，请稍后重试下单");
        };
    }
}
