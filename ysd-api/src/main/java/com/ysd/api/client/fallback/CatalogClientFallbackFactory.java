package com.ysd.api.client.fallback;

import com.ysd.api.client.CatalogClient;
import com.ysd.api.dto.DrugSnapshotDTO;
import com.ysd.api.dto.DrugInfoDTO;
import com.ysd.api.dto.DrugComboSnapshotDTO;
import com.ysd.exception.BaseException;
import com.ysd.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * CatalogClient 的熔断降级工厂：仅在「开启 Feign 熔断 + Sentinel 熔断触发」时被调用。
 *
 * <p>药品 / 药品组合数据 **不得编造**，因此直接抛业务异常（fail-fast），
 * 由消费方（order-service / ai-service）自行兜底处理。
 */
@Slf4j
@Component
public class CatalogClientFallbackFactory implements FallbackFactory<CatalogClient> {

    @Override
    public CatalogClient create(Throwable cause) {
        log.warn("CatalogClient 触发熔断降级", cause);
        return new CatalogClient() {
            @Override
            public Result<DrugSnapshotDTO> getDrugById(Long id) {
                throw new BaseException("药品服务繁忙，请稍后重试");
            }

            @Override
            public Result<DrugInfoDTO> getDrugInfoById(Long id) {
                throw new BaseException("药品服务繁忙，请稍后重试");
            }

            @Override
            public Result<DrugComboSnapshotDTO> getDrugComboById(Long id) {
                throw new BaseException("药品服务繁忙，请稍后重试");
            }

            @Override
            public Result<List<DrugInfoDTO>> searchDrugByName(String name) {
                throw new BaseException("药品服务繁忙，请稍后重试");
            }
        };
    }
}
