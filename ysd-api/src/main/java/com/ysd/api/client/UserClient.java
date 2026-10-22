package com.ysd.api.client;

import com.ysd.api.client.fallback.UserClientFallbackFactory;
import com.ysd.api.dto.AddressBookSnapshotDTO;
import com.ysd.api.interceptor.FeignRequestInterceptorConfiguration;
import com.ysd.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * user-service 的跨服务只读接口。
 * 服务端实现见 ysd-user-service 的 {@code com.ysd.controller.inner.InnerAddressBookController}。
 */
@FeignClient(name = "ysd-user-service", path = "/inner",
        configuration = FeignRequestInterceptorConfiguration.class,
        fallbackFactory = UserClientFallbackFactory.class)
public interface UserClient {

    /**
     * 按 id 查询地址簿快照（下单时取收货人 / 电话）。
     */
    @GetMapping("/addressBook/{id}")
    Result<AddressBookSnapshotDTO> getAddressBookById(@PathVariable("id") Long id);
}
