package com.ysd.config;

import com.ysd.interceptor.InnerIdentityInterceptor;
import com.ysd.interceptor.JwtTokenUserInterceptor;
import com.ysd.json.JacksonObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * 配置类，注册web层相关组件。
 */
@Configuration
@Slf4j
public class WebMvcConfiguration implements WebMvcConfigurer {

    @Autowired
    private JwtTokenUserInterceptor jwtTokenUserInterceptor;

    /**
     * 注册自定义拦截器
     *
     * @param registry
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        log.info("开始注册自定义拦截器...");
        registry.addInterceptor(jwtTokenUserInterceptor)
                .addPathPatterns("/user/user/**", "/user/addressBook/**")
                .excludePathPatterns("/user/user/login");
        // 内网接口：只透传身份，不做登录校验（外部访问由网关 403 屏蔽）
        registry.addInterceptor(new InnerIdentityInterceptor())
                .addPathPatterns("/inner/**");
    }

    /**
     * 扩展消息转换器，为 Jackson 换上项目自定义的 {@link JacksonObjectMapper}（统一日期格式）
     *
     * @param converters
     */
    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        log.info("扩展消息转换器...");
        for (HttpMessageConverter<?> converter : converters) {
            if (converter instanceof MappingJackson2HttpMessageConverter jacksonConverter) {
                jacksonConverter.setObjectMapper(new JacksonObjectMapper());
            }
        }
    }
}
