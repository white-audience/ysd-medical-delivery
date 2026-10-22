package com.ysd.controller.dto;

/**
 * AI 问答请求体。conversationId 由客户端在上一次响应中取得后回传，用于多轮记忆；
 * 首次对话可不传，由服务端生成并通过响应头 / done 事件返回。
 */
public record AiChatRequest(String message, String conversationId) {
}
