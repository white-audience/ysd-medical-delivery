package com.ysd.controller.vo;

import java.util.Map;

/**
 * RAG 检索诊断返回的单条片段：id、余弦相似度、来源文档、正文与完整元数据。
 * 仅用于评测与调参，不对外作为业务数据。
 */
public record RetrievedChunkVO(String id, Double score, String source, String text,
                               Map<String, Object> metadata) {
}
