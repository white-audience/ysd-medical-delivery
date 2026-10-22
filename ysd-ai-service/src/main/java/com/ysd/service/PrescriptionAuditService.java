package com.ysd.service;

import com.ysd.api.client.CatalogClient;
import com.ysd.api.dto.DrugInfoDTO;
import com.ysd.api.dto.PrescriptionCheckRequest;
import com.ysd.api.dto.PrescriptionCheckResponse;
import com.ysd.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 下单主链路的同步处方审核。
 *
 * <p>这里刻意不调用大模型：下单接口有 3 秒 Feign 超时，而一次 RAG + LLM 往返无法稳定满足；
 * 同时审核结论必须是确定性的。大模型/RAG 只保留在用户侧咨询和异步人工复核，
 * 同步链路负责“是否允许放行”的合规兜底。
 *
 * <p>所有下游异常一律返回 approved=false（fail-closed）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrescriptionAuditService {

    private static final int MAX_PRESCRIPTION_URL_LENGTH = 512;

    private final CatalogClient catalogClient;

    public PrescriptionCheckResponse check(PrescriptionCheckRequest request) {
        if (request == null || CollectionUtils.isEmpty(request.drugIds())) {
            return new PrescriptionCheckResponse(true, "未提交药品，无需处方审核", List.of());
        }

        List<Long> drugIds = request.drugIds().stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (drugIds.isEmpty()) {
            return new PrescriptionCheckResponse(true, "未提交药品，无需处方审核", List.of());
        }

        List<DrugInfoDTO> drugs = new ArrayList<>();
        try {
            for (Long drugId : drugIds) {
                Result<DrugInfoDTO> result = catalogClient.getDrugInfoById(drugId);
                if (result == null || result.getCode() == null || result.getCode() != 1
                        || result.getData() == null) {
                    log.warn("处方审核查询药品失败，drugId={}, result={}", drugId, result);
                    return reject("药品信息查询失败，无法完成处方审核，请稍后重试");
                }
                drugs.add(result.getData());
            }
        } catch (Exception e) {
            log.error("处方审核查询药品异常，drugIds={}", drugIds, e);
            return reject("药品信息服务不可用，无法完成处方审核，请稍后重试");
        }

        List<String> prescriptionDrugs = drugs.stream()
                .filter(d -> Integer.valueOf(1).equals(d.getPrescriptionType()))
                .map(DrugInfoDTO::getName)
                .filter(Objects::nonNull)
                .toList();

        if (prescriptionDrugs.isEmpty()) {
            return new PrescriptionCheckResponse(true, "购物车不含处方药，无需处方审核", List.of());
        }

        String prescriptionUrl = request.prescriptionUrl();
        if (prescriptionUrl == null || prescriptionUrl.isBlank()) {
            return reject("购物车包含处方药（" + String.join("、", prescriptionDrugs)
                    + "），请先上传处方后再下单");
        }
        if (prescriptionUrl.length() > MAX_PRESCRIPTION_URL_LENGTH) {
            return reject("处方凭证地址无效，请重新上传处方");
        }

        log.info("处方审核通过，userId={}, drugs={}", request.userId(), prescriptionDrugs);
        return new PrescriptionCheckResponse(true, "处方已上传，合规校验通过，待药师复核", prescriptionDrugs);
    }

    private PrescriptionCheckResponse reject(String reason) {
        log.warn("处方审核未通过：{}", reason);
        return new PrescriptionCheckResponse(false, reason, List.of());
    }
}
