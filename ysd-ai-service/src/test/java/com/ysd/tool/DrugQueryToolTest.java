package com.ysd.tool;

import com.ysd.api.client.CatalogClient;
import com.ysd.api.dto.DrugInfoDTO;
import com.ysd.result.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.when;

/**
 * 工具层失败路径：catalog-service 超时/异常时，DrugQueryTool 必须吞掉异常并返回兜底话术，
 * 让大模型继续组织回答，而不是把 500 抛给用户。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DrugQueryTool 失败降级")
class DrugQueryToolTest {

    @Mock
    private CatalogClient catalogClient;

    @InjectMocks
    private DrugQueryTool drugQueryTool;

    @Test
    @DisplayName("TOOL-01 下游 Feign 异常：返回兜底话术，不向上抛异常")
    void downstreamException_returnsFallback() {
        when(catalogClient.searchDrugByName("头孢"))
                .thenThrow(new RuntimeException("Read timed out"));

        assertThatCode(() -> drugQueryTool.queryDrugInfo("头孢"))
                .doesNotThrowAnyException();

        String result = drugQueryTool.queryDrugInfo("头孢");
        assertThat(result)
                .contains("暂不可用")
                .contains("请稍后重试")
                .doesNotContain("Exception")
                .doesNotContain("Read timed out");
    }

    @Test
    @DisplayName("TOOL-02 空关键词：提示用户输入，不调用下游")
    void blankKeyword_returnsPrompt() {
        String result = drugQueryTool.queryDrugInfo("  ");

        assertThat(result).isEqualTo("请输入药品名称或症状关键词。");
    }

    @Test
    @DisplayName("TOOL-03 查询成功：处方药带明确购买提示")
    void prescriptionDrug_containsUploadHint() {
        DrugInfoDTO rx = new DrugInfoDTO(1L, "阿莫西林胶囊", 1, "石药集团",
                "国药准字H13023964", "上呼吸道感染", "青霉素过敏者禁用");
        when(catalogClient.searchDrugByName("阿莫西林")).thenReturn(Result.success(List.of(rx)));

        String result = drugQueryTool.queryDrugInfo("阿莫西林");

        assertThat(result)
                .contains("【处方药】")
                .contains("阿莫西林胶囊")
                .contains("需凭处方购买");
    }
}
