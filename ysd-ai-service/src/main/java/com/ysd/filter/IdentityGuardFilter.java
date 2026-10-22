package com.ysd.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ysd.result.Result;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 只接受网关注入的身份头。AI 服务端口在部署时应仅对内网/网关开放，
 * 本过滤器用于在应用层兜底：缺少 X-Identity-Id 的 /ai/** 请求直接 401，
 * 防止绕过网关后匿名消耗大模型 Token。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class IdentityGuardFilter extends OncePerRequestFilter {

    public static final String IDENTITY_HEADER = "X-Identity-Id";

    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!path.startsWith("/ai/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String identity = request.getHeader(IDENTITY_HEADER);
        if (identity == null || identity.isBlank()) {
            log.warn("拒绝缺少 {} 的直连请求: {} {}", IDENTITY_HEADER, request.getMethod(), path);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            Result<Void> body = new Result<>();
            body.setCode(401);
            body.setMsg("缺少网关身份头，请通过网关访问");
            objectMapper.writeValue(response.getWriter(), body);
            return;
        }

        filterChain.doFilter(request, response);
    }
}
