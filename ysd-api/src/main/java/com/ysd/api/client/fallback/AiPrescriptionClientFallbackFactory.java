package com.ysd.api.client.fallback;

import com.ysd.api.client.AiPrescriptionClient;
import com.ysd.api.dto.PrescriptionCheckRequest;
import com.ysd.api.dto.PrescriptionCheckResponse;
import com.ysd.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * AI 审核熔断降级：涉及处方药安全，必须 fail-closed。
 * 返回 approved=false 而不是抛异常，方便 order-service 统一按“审核不通过”处理。
 */
@Slf4j
@Component
public class AiPrescriptionClientFallbackFactory implements FallbackFactory<AiPrescriptionClient> {

    @Override
    public AiPrescriptionClient create(Throwable cause) {
        log.warn("AiPrescriptionClient 触发熔断降级，按 fail-closed 拦截", cause);
        return request -> Result.success(new PrescriptionCheckResponse(
                false, "处方审核服务不可用，为保障用药安全暂不能下单，请稍后重试", List.of()));
    }
}
