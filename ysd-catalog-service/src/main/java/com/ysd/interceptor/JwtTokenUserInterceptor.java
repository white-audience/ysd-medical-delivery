package com.ysd.interceptor;

import com.ysd.constant.JwtClaimsConstant;
import com.ysd.context.BaseContext;
import com.ysd.properties.JwtProperties;
import com.ysd.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * jwt令牌校验的拦截器
 */
@Component
@Slf4j
public class JwtTokenUserInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtProperties jwtProperties;

    /**
     * 校验jwt
     *
     * @param request
     * @param response
     * @param handler
     * @return
     * @throws Exception
     */
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {


        log.debug("当前线程的id：{}", Thread.currentThread().getId());



        //判断当前拦截到的是Controller的方法还是其他资源
        if (!(handler instanceof HandlerMethod)) {
            //当前拦截到的不是动态方法，直接放行
            return true;
        }

        //1、优先读取网关透传的身份请求头
        String headerId = request.getHeader("X-Identity-Id");
        if (headerId != null) {
            //网关模式：网关已解析token，直接使用透传的id
            BaseContext.setCurrentId(Long.valueOf(headerId));
            return true;
        }

        //2、兼容模式：未经过网关的请求，自行解析令牌
        String token = request.getHeader(jwtProperties.getUserTokenName());

        //3、校验令牌
        try {
            log.debug("jwt校验");
            Claims claims = JwtUtil.parseJWT(jwtProperties.getUserSecretKey(), token);
            Long userId = Long.valueOf(claims.get(JwtClaimsConstant.USER_ID).toString());
            log.info("当前用户id：{}", userId);
            BaseContext.setCurrentId(userId);
            //4、通过，放行
            return true;
        } catch (Exception ex) {
            //5、不通过，响应401状态码
            response.setStatus(401);
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 线程池复用：请求结束后必须清理，否则身份会泄漏到下一个请求
        BaseContext.removeCurrentId();
    }
}
