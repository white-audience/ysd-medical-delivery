package com.ysd.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ysd.controller.dto.AiChatRequest;
import com.ysd.controller.vo.AiAnswerVO;
import com.ysd.result.Result;
import com.ysd.tool.DrugQueryTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 入参校验：空问题/超长问题必须在进入大模型前被拦截，
 * 既不消耗 Token，也不会因为空 prompt 抛 500。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AI 问答入参校验")
class AiDrugControllerValidationTest {

    @Mock
    private ChatClient chatClient;
    @Mock
    private DrugQueryTool drugQueryTool;
    @Mock
    private VectorStore vectorStore;

    private AiDrugController controller;

    @BeforeEach
    void setUp() {
        ChatClient.Builder builder = org.mockito.Mockito.mock(ChatClient.Builder.class, Answers.RETURNS_SELF);
        when(builder.build()).thenReturn(chatClient);

        controller = new AiDrugController(builder, drugQueryTool, vectorStore,
                new ObjectMapper(), 4, 0.5);
    }

    @Test
    @DisplayName("CHAT-01 空问题：返回 400 + 提示，不调用模型")
    void blankMessage_rejectedWithoutCallingModel() {
        ResponseEntity<Result<AiAnswerVO>> response =
                controller.chat(new AiChatRequest("   ", null), "user-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isZero();
        assertThat(response.getBody().getMsg()).contains("请输入");
        verify(chatClient, never()).prompt();
    }

    @Test
    @DisplayName("CHAT-02 超长问题：返回 400，不调用模型")
    void tooLongMessage_rejectedWithoutCallingModel() {
        ResponseEntity<Result<AiAnswerVO>> response =
                controller.chat(new AiChatRequest("药".repeat(2001), null), "user-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMsg()).contains("2000");
        verify(chatClient, never()).prompt();
    }
}
