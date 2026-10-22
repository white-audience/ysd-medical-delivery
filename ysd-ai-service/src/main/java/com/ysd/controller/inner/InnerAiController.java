package com.ysd.controller.inner;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.ysd.api.dto.PrescriptionCheckRequest;
import com.ysd.api.dto.PrescriptionCheckResponse;
import com.ysd.result.Result;
import com.ysd.service.PrescriptionAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 内网接口：供 order-service 在下单前同步调用。
 * 网关只路由 /ai/**，因此 /inner/ai/** 只能通过服务发现在内网访问。
 */
@Slf4j
@RestController
@RequestMapping("/inner/ai")
@RequiredArgsConstructor
public class InnerAiController {

    private final PrescriptionAuditService prescriptionAuditService;

    @PostMapping("/check-prescription")
    @SentinelResource(value = "aiPrescriptionCheck", blockHandler = "handleBlock")
    public Result<PrescriptionCheckResponse> checkPrescription(
            @RequestBody(required = false) PrescriptionCheckRequest request) {
        return Result.success(prescriptionAuditService.check(request));
    }

    public Result<PrescriptionCheckResponse> handleBlock(PrescriptionCheckRequest request,
                                                         BlockException ex) {
        log.warn("处方审核接口被限流，按 fail-closed 拦截，userId={}",
                request == null ? null : request.userId());
        return Result.success(new PrescriptionCheckResponse(
                false, "处方审核服务繁忙，为保障用药安全暂不能下单，请稍后重试", List.of()));
    }
}
