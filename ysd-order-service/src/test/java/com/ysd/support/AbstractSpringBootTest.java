package com.ysd.support;

import com.ysd.constant.JwtClaimsConstant;
import com.ysd.properties.JwtProperties;
import com.ysd.utils.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

/**
 * 所有需要启动 Spring 上下文的测试的基类。
 *
 * <ul>
 *   <li>profile 固定为 {@code test}：H2（MODE=MySQL）替代 MySQL，embedded redis 替代 Redis；</li>
 *   <li>{@link EmbeddedRedisInitializer} 保证真实 redis-server 在上文创建前已就绪；</li>
 *   <li>统一注入 {@link MockMvc}，拦截器 / 异常处理器全链路生效。</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@ContextConfiguration(initializers = EmbeddedRedisInitializer.class)
public abstract class AbstractSpringBootTest {

    /** 管理端令牌的请求头名，对应 application.yml 的 ysd.jwt.admin-token-name = token */
    public static final String ADMIN_TOKEN_HEADER = "token";

    /** 用户端令牌的请求头名，对应 application.yml 的 ysd.jwt.user-token-name = authentication */
    public static final String USER_TOKEN_HEADER = "authentication";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JwtProperties jwtProperties;

    /** 生成一个合法的管理端令牌，claims 里放员工 id */
    protected String adminToken(long empId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.EMP_ID, empId);
        return JwtUtil.createJWT(jwtProperties.getAdminSecretKey(), jwtProperties.getAdminTtl(), claims);
    }

    /** 生成一个合法的用户端令牌，claims 里放用户 id */
    protected String userToken(long userId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.USER_ID, userId);
        return JwtUtil.createJWT(jwtProperties.getUserSecretKey(), jwtProperties.getUserTtl(), claims);
    }

    /** 生成一个已过期的管理端令牌（ttl 为负数） */
    protected String expiredAdminToken(long empId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.EMP_ID, empId);
        return JwtUtil.createJWT(jwtProperties.getAdminSecretKey(), -60_000L, claims);
    }

    /** 生成一个用错误密钥签名的管理端令牌（模拟伪造 / 篡改） */
    protected String forgedAdminToken(long empId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.EMP_ID, empId);
        return JwtUtil.createJWT("wrong-secret-key", jwtProperties.getAdminTtl(), claims);
    }
}
