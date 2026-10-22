package com.ysd.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 多轮记忆隔离：记忆键 = userId + ":" + conversationId。
 * 两个用户即使拿到同一个 conversationId，也不能读到对方的上下文。
 */
@DisplayName("多轮对话记忆隔离")
class ConversationMemoryIsolationTest {

    @Test
    @DisplayName("MEM-01 相同 conversationId、不同 userId：记忆键必须不同")
    void sameConversationId_differentUsers_haveDifferentKeys() {
        String keyA = ConversationKeys.memoryKey("1001", "conv-same");
        String keyB = ConversationKeys.memoryKey("1002", "conv-same");

        assertThat(keyA).isEqualTo("1001:conv-same");
        assertThat(keyB).isEqualTo("1002:conv-same");
        assertThat(keyA).isNotEqualTo(keyB);
    }

    @Test
    @DisplayName("MEM-02 写入 A 的上下文后，B 读不到 A 的消息")
    void messagesWrittenByUserA_areInvisibleToUserB() {
        MessageWindowChatMemory memory = MessageWindowChatMemory.builder()
                .maxMessages(20)
                .build();

        String keyA = ConversationKeys.memoryKey("1001", "conv-same");
        String keyB = ConversationKeys.memoryKey("1002", "conv-same");

        memory.add(keyA, List.of(new UserMessage("A 用户：我对青霉素过敏")));
        memory.add(keyB, List.of(new UserMessage("B 用户：我头痛")));

        List<Message> messagesA = memory.get(keyA);
        List<Message> messagesB = memory.get(keyB);

        assertThat(messagesA).extracting(Message::getText)
                .containsExactly("A 用户：我对青霉素过敏");
        assertThat(messagesB).extracting(Message::getText)
                .containsExactly("B 用户：我头痛");
        assertThat(messagesA).extracting(Message::getText)
                .doesNotContain("B 用户：我头痛");
    }
}
