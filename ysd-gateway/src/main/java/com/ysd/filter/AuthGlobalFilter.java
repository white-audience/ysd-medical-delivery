package com.ysd.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ysd.constant.JwtClaimsConstant;
import com.ysd.properties.JwtProperties;
import com.ysd.result.Result;
import com.ysd.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(AuthGlobalFilter.class);

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private ObjectMapper objectMapper;

    // 1. 白名单：这些路径不用检查 token
    private static final List<String> WHITE_LIST = List.of(
            "/admin/employee/login",
            "/user/user/login",
            "/user/shop/status",
            "/notify/",
            "/ws/",
            "/doc.html",
            "/webjars/"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 1. 剥离客户端自带的身份头，防止绕过网关伪造身份
        exchange = exchange.mutate()
                .request(exchange.getRequest().mutate()
                        .headers(headers -> headers.remove("X-Identity-Id"))
                        .build())
                .build();

        // 2. 拿到请求路径
        String path = exchange.getRequest().getURI().getPath();

        // 2.1 内网接口（/inner/**）不对网关暴露：服务间用 Feign 直连，不经过网关。
        //     外部访问一律 403，避免落到 /** 兜底路由后触达下游服务。
        if (path.startsWith("/inner/")) {
            return writeError(exchange, HttpStatus.FORBIDDEN, 403, "无权限访问");
        }

        // 3. 如果在白名单里，直接放行
        for (String white : WHITE_LIST) {
            if (path.startsWith(white)) {
                return chain.filter(exchange);
            }
        }

        // 4. 判断是管理端还是用户端
        boolean isAdmin = path.startsWith("/admin/");

        // 5. 从请求头里取 token（管理端叫 token，用户端叫 authentication）
        String token;
        if (isAdmin) {
            token = exchange.getRequest().getHeaders().getFirst("token");
        } else {
            token = exchange.getRequest().getHeaders().getFirst("authentication");
        }

        // 6. 没有 token，返回 401
        if (token == null || token.isEmpty()) {
            log.warn("请求缺少 token, path={}", path);
            return writeUnauthorized(exchange);
        }

        // 7. 解析 token，拿到 id
        try {
            Claims claims;
            String idKey;
            if (isAdmin) {
                claims = JwtUtil.parseJWT(jwtProperties.getAdminSecretKey(), token);
                idKey = JwtClaimsConstant.EMP_ID;
            } else {
                claims = JwtUtil.parseJWT(jwtProperties.getUserSecretKey(), token);
                idKey = JwtClaimsConstant.USER_ID;
            }

            // 8. 把 id 写进新请求头，转发给下游
            String id = claims.get(idKey).toString();
            ServerHttpRequest newRequest = exchange.getRequest().mutate()
                    .header("X-Identity-Id", id)
                    .build();

            return chain.filter(exchange.mutate().request(newRequest).build());

        } catch (Exception e) {
            // token 解析失败，返回 401
            log.warn("JWT 校验失败: {}", e.getMessage());
            return writeUnauthorized(exchange);
        }
    }

    private Mono<Void> writeUnauthorized(ServerWebExchange exchange) {
        return writeError(exchange, HttpStatus.UNAUTHORIZED, 401, "未登录或登录已过期");
    }

    // 统一写出指定 HTTP 状态码 + JSON 响应体，避免前端拿到空 body
    private Mono<Void> writeError(ServerWebExchange exchange, HttpStatus status, int code, String msg) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        Result<Void> body = new Result<>();
        body.setCode(code);
        body.setMsg(msg);
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(body);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            log.error("写出 {} 响应体失败", code, e);
            return response.setComplete();
        }
    }

    @Override
    public int getOrder() {
        return -100;  // 数字越小越先执行
    }
}