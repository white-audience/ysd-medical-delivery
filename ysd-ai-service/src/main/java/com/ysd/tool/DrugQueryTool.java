package com.ysd.tool;

import com.ysd.api.client.CatalogClient;
import com.ysd.api.dto.DrugInfoDTO;
import com.ysd.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
public class DrugQueryTool {

    private final CatalogClient catalogClient;

    public DrugQueryTool(CatalogClient catalogClient) {
        this.catalogClient = catalogClient;
    }

    @Tool(description = "根据药品名称或症状关键词查询平台在售药品信息。" +
            "返回药品名、是否处方药、生产厂家、批准文号、适应症、禁忌。" +
            "处方药（prescriptionType=1）必须提示用户需上传处方后才能购买。")
    public String queryDrugInfo(
            @ToolParam(description = "药品名称或症状关键词，如：布洛芬、头痛、阿莫西林") String keyword) {

        if (keyword == null || keyword.isBlank()) {
            return "请输入药品名称或症状关键词。";
        }

        log.debug("查询药品，参数：{}", keyword);
        Result<List<DrugInfoDTO>> result;
        try {
            result = catalogClient.searchDrugByName(keyword);
        } catch (Exception e) {
            log.error("调用 catalog-service 查询药品失败，keyword={}", keyword, e);
            return "药品查询服务暂不可用，请稍后重试。";
        }

        if (result == null || result.getData() == null || result.getData().isEmpty()) {
            return "平台暂无与「" + keyword + "」相关的在售药品。";
        }

        log.debug("药品查询返回 {} 条", result.getData().size());
        return result.getData().stream()
                .map(this::formatDrug)
                .collect(Collectors.joining("\n\n"));
    }

    private String formatDrug(DrugInfoDTO d) {
        boolean isPrescription = d.getPrescriptionType() != null && d.getPrescriptionType() == 1;
        String type = isPrescription ? "【处方药】" : "【OTC非处方药】";

        StringBuilder sb = new StringBuilder();
        sb.append(type).append(" ").append(d.getName()).append("\n");
        sb.append("  生产厂家：").append(nullSafe(d.getManufacturer())).append("\n");
        sb.append("  批准文号：").append(nullSafe(d.getApprovalNumber())).append("\n");
        sb.append("  适应症：").append(nullSafe(d.getIndications())).append("\n");
        sb.append("  禁忌：").append(nullSafe(d.getContraindications()));
        if (isPrescription) {
            sb.append("\n  ⚠️ 处方药，需凭处方购买，请上传处方后再下单。");
        }
        return sb.toString();
    }

    private String nullSafe(String s) {
        return s == null || s.isEmpty() ? "暂无" : s;
    }
}