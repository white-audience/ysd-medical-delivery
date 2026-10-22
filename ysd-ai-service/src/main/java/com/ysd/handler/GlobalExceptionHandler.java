package com.ysd.handler;

import com.ysd.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * AI 服务全局异常兜底：大模型超时、下游服务宕机等异常统一转换为 Result，
 * 避免直接向调用方返回 500 白页。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public Result<String> handleException(Exception e) {
        log.error("AI 服务处理请求异常", e);
        return Result.error("AI 服务繁忙，请稍后再试");
    }
}
