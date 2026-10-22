package com.ysd.api.client;

import com.ysd.api.client.fallback.CatalogClientFallbackFactory;
import com.ysd.api.dto.DrugSnapshotDTO;
import com.ysd.api.dto.DrugInfoDTO;
import com.ysd.api.dto.DrugComboSnapshotDTO;
import com.ysd.api.interceptor.FeignRequestInterceptorConfiguration;
import com.ysd.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * catalog-service 的跨服务只读接口。
 * 服务端实现见 ysd-catalog-service 的 {@code com.ysd.controller.inner.InnerDrugController} /
 * {@code InnerDrugComboController}。
 */
@FeignClient(name = "ysd-catalog-service", path = "/inner",
        configuration = FeignRequestInterceptorConfiguration.class,
        fallbackFactory = CatalogClientFallbackFactory.class)
public interface CatalogClient {

    /**
     * 按 id 查询药品快照（加购物车时取 name / image / price）。
     */
    @GetMapping("/drug/{id}")
    Result<DrugSnapshotDTO> getDrugById(@PathVariable("id") Long id);

    /**
     * 按 id 查询药品完整信息（含 prescriptionType / 禁忌），供下单处方审核使用。
     */
    @GetMapping("/drug/{id}/info")
    Result<DrugInfoDTO> getDrugInfoById(@PathVariable("id") Long id);

    /**
     * 按 id 查询药品组合快照（加购物车时取 name / image / price）。
     */
    @GetMapping("/drug-combo/{id}")
    Result<DrugComboSnapshotDTO> getDrugComboById(@PathVariable("id") Long id);

    /**
     * 按药品名称/症状关键词模糊查询药品信息（供 AI Agent 用药咨询调用）。
     */
    @GetMapping("/drug/search")
    Result<List<DrugInfoDTO>> searchDrugByName(@RequestParam("name") String name);
}
