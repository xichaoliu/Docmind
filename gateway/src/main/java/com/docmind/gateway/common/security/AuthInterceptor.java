package com.docmind.gateway.common.security;

import com.docmind.gateway.common.exception.BizException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.AsyncHandlerInterceptor;

@Component
@RequiredArgsConstructor
public class AuthInterceptor implements AsyncHandlerInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 跨域预检请求不带 Authorization，必须放行，否则前端所有请求都卡在预检上
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            throw new BizException(401, "未登录");
        }

        try {
            // TODO 查库看用户是否真实存在
            CurrentUser.set(jwtUtil.parseUserId(header.substring(BEARER_PREFIX.length())));
        } catch (JwtException | IllegalArgumentException e) {
            throw new BizException(401, "登录已过期，请重新登录");
        }
        return true;
    }

    /** 普通请求走完就清理 */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        CurrentUser.clear();
    }

    /**
     * SSE 流式接口是异步请求：方法体返回 Flux 后 Servlet 线程立刻归还线程池，
     * 这时 afterCompletion 还没执行，所以要在并发处理开始时先清一次。
     */
    @Override
    public void afterConcurrentHandlingStarted(HttpServletRequest request,
                                               HttpServletResponse response, Object handler) {
        CurrentUser.clear();
    }
}
