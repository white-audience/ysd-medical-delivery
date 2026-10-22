package com.ysd.controller;

import com.ysd.api.client.CatalogClient;
import com.ysd.api.dto.DrugInfoDTO;
import com.ysd.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RAG 空召回：没有规则依据时不允许调用模型“自由发挥”，
 * 直接返回可识别的错误提示，这是防幻觉的最后一道闸。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("处方审核 RAG 失败路径")
class AiPrescriptionControllerTest {

    @Mock
    private CatalogClient catalogClient;
    @Mock
    private VectorStore vectorStore;
    @Mock
    private ChatClient chatClient;
    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;
    @Mock
    private ChatClient.CallResponseSpec responseSpec;
    @Mock
    private ChatClient.Builder builder;

    private AiPrescriptionController controller;

    @BeforeEach
    void setUp() {
        when(builder.build()).thenReturn(chatClient);
        controller = new AiPrescriptionController(builder, catalogClient, vectorStore);
    }

    @Test
    @DisplayName("RAG-01 向量库返回空：返回错误提示，绝不调用模型")
    void emptyRetrieval_doesNotCallModel() {
        when(catalogClient.searchDrugByName("阿莫西林"))
                .thenReturn(Result.success(List.of(prescriptionDrug())));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        Result<String> result = controller.check("阿莫西林");

        assertThat(result.getCode()).isZero();
        assertThat(result.getMsg()).contains("未检索到平台处方药购买规则");
        verify(chatClient, never()).prompt();
    }

    @Test
    @DisplayName("RAG-02 检索到规则：规则原文进入 prompt")
    void retrievalPresent_contextFlowsIntoPrompt() {
        when(catalogClient.searchDrugByName("阿莫西林"))
                .thenReturn(Result.success(List.of(prescriptionDrug())));
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(new Document("处方药购买规则：必须上传处方并由药师审核")));

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("审核通知：请上传处方");

        Result<String> result = controller.check("阿莫西林");

        assertThat(result.getCode()).isEqualTo(1);
        assertThat(result.getData()).isEqualTo("审核通知：请上传处方");

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).user(promptCaptor.capture());
        assertThat(promptCaptor.getValue())
                .contains("处方药购买规则：必须上传处方并由药师审核")
                .contains("阿莫西林胶囊");
    }

    private DrugInfoDTO prescriptionDrug() {
        return new DrugInfoDTO(20L, "阿莫西林胶囊", 1, "石药集团",
                "国药准字H13023964", "上呼吸道感染", "青霉素过敏者禁用");
    }
}
