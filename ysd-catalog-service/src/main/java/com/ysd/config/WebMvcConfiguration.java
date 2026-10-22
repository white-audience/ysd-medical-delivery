package com.ysd.config;

import com.ysd.interceptor.InnerIdentityInterceptor;
import com.ysd.interceptor.JwtTokenAdminInterceptor;
import com.ysd.interceptor.JwtTokenUserInterceptor;
import com.ysd.json.JacksonObjectMapper;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * 药品目录服务的 web 层配置。
 *
 * <p>与 admin/report 服务不同，本服务同时对外提供管理端（/admin/category|drug|drugCombo）与
 * 用户端（/user/category|drug|drugCombo），因此需要**同时注册两个拦截器**，
 * 并分别暴露管理端 / 用户端两个 knife4j 文档分组。
 */
@Configuration
@Slf4j
public class WebMvcConfiguration implements WebMvcConfigurer {

    @Autowired
    private JwtTokenAdminInterceptor jwtTokenAdminInterceptor;
    @Autowired
    private JwtTokenUserInterceptor jwtTokenUserInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        log.info("开始注册自定义拦截器...");
        // 管理端：分类 / 药品 / 药品组合
        registry.addInterceptor(jwtTokenAdminInterceptor)
                .addPathPatterns("/admin/category/**", "/admin/drug/**", "/admin/drug-combo/**");
        // 用户端：分类 / 药品 / 药品组合（沿用迁移前行为：C 端浏览也需带 token）
        registry.addInterceptor(jwtTokenUserInterceptor)
                .addPathPatterns("/user/category/**", "/user/drug/**", "/user/drug-combo/**");
        // 内网接口：只透传身份，不做登录校验（外部访问由网关 403 屏蔽）
        registry.addInterceptor(new InnerIdentityInterceptor())
                .addPathPatterns("/inner/**");
    }

    /**
     * 接口文档整体信息
     */
    @Bean
    public OpenAPI ysdOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("药速达智慧配送平台 - 药品目录服务接口文档")
                .version("2.0")
                .description("分类 / 药品 / 药品组合"));
    }

    @Bean
    public GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder()
                .group("管理端接口")
                .packagesToScan("com.ysd.controller.admin")
                .build();
    }

    @Bean
    public GroupedOpenApi userApi() {
        return GroupedOpenApi.builder()
                .group("用户端接口")
                .packagesToScan("com.ysd.controller.user")
                .build();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        log.info("开始进行静态资源映射...");
        registry.addResourceHandler("/doc.html").addResourceLocations("classpath:/META-INF/resources/");
        registry.addResourceHandler("/webjars/**").addResourceLocations("classpath:/META-INF/resources/webjars/");
    }

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
