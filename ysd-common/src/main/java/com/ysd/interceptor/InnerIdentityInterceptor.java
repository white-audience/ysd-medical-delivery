package com.ysd.interceptor;

import com.ysd.context.BaseContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 内网接口（{@code /inner/**}）身份拦截器。
 *
 * <p>只做一件事：读取 Feign 出站拦截器透传的 {@code X-Identity-Id} 写入 {@link BaseContext}，
 * <b>不做登录校验</b>——外部访问由网关 403 屏蔽，服务间调用由 FeignRequestInterceptor 带头。
 *
 * <p>刻意不加 {@code @Component}：由各服务在 WebMvcConfiguration 里手动注册，避免被组件扫描自动生效。
 */
public class InnerIdentityInterceptor implements HandlerInterceptor {

    public static final String IDENTITY_HEADER = "X-Identity-Id";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        String headerId = request.getHeader(IDENTITY_HEADER);
        if (headerId != null && !headerId.isEmpty()) {
            BaseContext.setCurrentId(Long.valueOf(headerId));
        }
        return true;
    }
}
