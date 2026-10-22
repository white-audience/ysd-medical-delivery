package com.ysd.api.interceptor;

import com.ysd.context.BaseContext;
import feign.RequestInterceptor;
import feign.RequestTemplate;

/**
 * Feign 出站拦截器：把当前请求身份（BaseContext 中的 userId）透传到下游服务的 X-Identity-Id 头。
 *
 * <p>与网关下游拦截器（JwtTokenUserInterceptor）读取的是同一个头，保证跨服务调用链身份一致。
 * 依赖同线程同步调用（Feign 默认客户端满足）；若改用异步客户端需另行处理 ThreadLocal 传递。
 */
public class FeignRequestInterceptor implements RequestInterceptor {

    public static final String IDENTITY_HEADER = "X-Identity-Id";

    @Override
    public void apply(RequestTemplate template) {
        Long id = BaseContext.getCurrentId();
        if (id != null) {
            template.header(IDENTITY_HEADER, String.valueOf(id));
        }
    }
}
