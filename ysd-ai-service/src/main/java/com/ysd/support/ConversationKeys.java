package com.ysd.support;

/**
 * 对话记忆键：必须同时包含用户 ID 与 conversationId，
 * 防止不同用户拿到相同 conversationId 时互相读到上下文。
 */
public final class ConversationKeys {

    private ConversationKeys() {
    }

    public static String memoryKey(String userId, String conversationId) {
        return userId + ":" + conversationId;
    }
}
