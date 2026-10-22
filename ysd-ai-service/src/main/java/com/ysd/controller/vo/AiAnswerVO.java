package com.ysd.controller.vo;

/**
 * 阻塞式问答的返回结构：answer 为模型回答，conversationId 供客户端下一轮回传。
 */
public record AiAnswerVO(String conversationId, String answer) {
}
