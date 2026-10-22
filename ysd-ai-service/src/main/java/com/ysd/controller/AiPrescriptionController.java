package com.ysd.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.ysd.api.client.CatalogClient;
import com.ysd.api.dto.DrugInfoDTO;
import com.ysd.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 处方药审核接口：不依赖大模型的工具规划，由 Java 代码按固定步骤编排，
 * 保证严肃业务场景下的执行顺序与结果确定性。
 */
@Slf4j
@RestController
@RequestMapping("/ai/prescription")
public class AiPrescriptionController {

    private static final String PRESCRIPTION_RULE_QUERY = "处方药购买规则";

    private final CatalogClient catalogClient;
    private final VectorStore vectorStore;
    private final ChatClient chatClient;

    public AiPrescriptionController(ChatClient.Builder builder, CatalogClient catalogClient, VectorStore vectorStore) {
        this.catalogClient = catalogClient;
        this.vectorStore = vectorStore;
        this.chatClient = builder.build();
    }

    @GetMapping("/check")
    @SentinelResource(value = "aiQuery", blockHandler = "handleBlock")
    public Result<String> check(@RequestParam("drugName") String drugName) {
        if (drugName == null || drugName.isBlank()) {
            return Result.error("请输入要审核的药品名称");
        }

        // 步骤 1：查询药品
        Result<List<DrugInfoDTO>> queryResult;
        try {
            queryResult = catalogClient.searchDrugByName(drugName);
        } catch (Exception e) {
            log.error("调用 catalog-service 查询药品失败，drugName={}", drugName, e);
            return Result.error("药品查询服务暂不可用，请稍后重试");
        }

        // 步骤 2：查不到直接返回
        if (queryResult == null || queryResult.getData() == null || queryResult.getData().isEmpty()) {
            return Result.error("未查询到药品「" + drugName + "」");
        }

        DrugInfoDTO drug = queryResult.getData().stream()
                .filter(d -> drugName.equals(d.getName()))
                .findFirst()
                .orElse(queryResult.getData().get(0));

        boolean isPrescription = drug.getPrescriptionType() != null && drug.getPrescriptionType() == 1;
        if (!isPrescription) {
            return Result.success("「" + drug.getName() + "」为非处方药（OTC），无需上传处方，可直接购买。");
        }

        // 步骤 3：处方药 → RAG 检索平台购买规则
        String ruleContext = retrievePrescriptionRules();
        if (ruleContext == null) {
            // 检索为空时不调用模型：没有依据的回答只能是编造
            return Result.error("未检索到平台处方药购买规则，暂无法完成审核，请稍后重试");
        }

        // 步骤 4：药品数据 + RAG 结果 + 固定 prompt → 生成审核通知
        String prompt = buildAuditPrompt(drug, ruleContext);
        String notice = chatClient.prompt()
                .system("你是药速达平台的处方药合规审核助手。只依据用户提供的药品信息和平台规则撰写审核通知，不得编造。")
                .user(prompt)
                .call()
                .content();
        return Result.success(notice);
    }

    private String retrievePrescriptionRules() {
        try {
            List<Document> documents = vectorStore.similaritySearch(SearchRequest.builder()
                    .query(PRESCRIPTION_RULE_QUERY)
                    .topK(4)
                    .similarityThreshold(0.5)
                    .build());
            if (documents == null || documents.isEmpty()) {
                log.warn("RAG 未检索到处方药购买规则");
                return null;
            }
            return documents.stream()
                    .map(Document::getText)
                    .collect(Collectors.joining("\n\n"));
        } catch (Exception e) {
            log.error("RAG 检索处方药购买规则失败", e);
            return null;
        }
    }

    private String buildAuditPrompt(DrugInfoDTO drug, String ruleContext) {
        return """
                请根据以下信息生成一段严谨的处方药审核通知。

                【药品信息】
                药品名称：%s
                药品属性：处方药
                生产厂家：%s
                批准文号：%s
                适应症：%s
                禁忌：%s

                【平台处方药购买规则】
                %s

                要求：
                1. 明确告知用户该药品为处方药，本次购买已被拦截。
                2. 说明拦截原因：处方药必须凭执业医师处方购买。
                3. 提示用户上传有效处方后重新下单。
                4. 语言严谨、简洁，不得编造上述信息之外的内容。
                """.formatted(
                drug.getName(),
                nullSafe(drug.getManufacturer()),
                nullSafe(drug.getApprovalNumber()),
                nullSafe(drug.getIndications()),
                nullSafe(drug.getContraindications()),
                ruleContext);
    }

    private String nullSafe(String value) {
        return value == null || value.isBlank() ? "暂无" : value;
    }

    public Result<String> handleBlock(String drugName, BlockException ex) {
        log.warn("处方审核接口被限流，drugName={}", drugName);
        return Result.error("请求过于频繁，请稍后重试");
    }
}
