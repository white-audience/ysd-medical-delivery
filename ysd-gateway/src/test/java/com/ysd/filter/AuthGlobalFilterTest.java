package com.ysd.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ysd.constant.JwtClaimsConstant;
import com.ysd.properties.JwtProperties;
import com.ysd.utils.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AuthGlobalFilterTest {

    private static final String USER_SECRET = "test-user-secret";

    private AuthGlobalFilter filter;

    @BeforeEach
    void setUp() {
        filter = new AuthGlobalFilter();
        JwtProperties properties = new JwtProperties();
        properties.setUserSecretKey(USER_SECRET);
        properties.setAdminSecretKey("test-admin-secret");
        ReflectionTestUtils.setField(filter, "jwtProperties", properties);
        ReflectionTestUtils.setField(filter, "objectMapper", new ObjectMapper());
    }

    @Test
    void stripsClientIdentityHeaderOnWhitelistedPath() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/ws/123")
                        .header("X-Identity-Id", "999")
                        .build());

        ServerHttpRequest forwarded = forward(exchange);

        assertThat(forwarded.getHeaders().containsKey("X-Identity-Id")).isFalse();
    }

    @Test
    void replacesForgedHeaderWithTrustedUserId() {
        String token = JwtUtil.createJWT(USER_SECRET, 60_000,
                Map.of(JwtClaimsConstant.USER_ID, 42L));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/user/drug")
                        .header("authentication", token)
                        .header("X-Identity-Id", "999")
                        .build());

        ServerHttpRequest forwarded = forward(exchange);

        assertThat(forwarded.getHeaders().getFirst("X-Identity-Id")).isEqualTo("42");
    }

    private ServerHttpRequest forward(ServerWebExchange exchange) {
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        filter.filter(exchange, ex -> {
            forwarded.set(ex);
            return Mono.empty();
        }).block();

        assertThat(forwarded.get()).isNotNull();
        return forwarded.get().getRequest();
    }
}
