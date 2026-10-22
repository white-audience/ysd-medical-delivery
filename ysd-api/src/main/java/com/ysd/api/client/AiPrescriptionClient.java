package com.ysd.api.client;

import com.ysd.api.client.fallback.AiPrescriptionClientFallbackFactory;
import com.ysd.api.dto.PrescriptionCheckRequest;
import com.ysd.api.dto.PrescriptionCheckResponse;
import com.ysd.api.interceptor.FeignRequestInterceptorConfiguration;
import com.ysd.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * ai-service 的内部处方审核接口。仅由 order-service 通过 Nacos 直接调用，
 * 不经过网关；服务端路径见 {@code com.ysd.controller.inner.InnerAiController}。
 */
@FeignClient(name = "ysd-ai-service", path = "/inner/ai",
        configuration = FeignRequestInterceptorConfiguration.class,
        fallbackFactory = AiPrescriptionClientFallbackFactory.class)
public interface AiPrescriptionClient {

    @PostMapping("/check-prescription")
    Result<PrescriptionCheckResponse> checkPrescription(@RequestBody PrescriptionCheckRequest request);
}
