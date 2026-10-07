package com.docmind.gateway.common.exception;

import com.docmind.gateway.common.result.Result;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("接口异常", e);
        return Result.error("服务器内部错误，请稍后重试");
    }

    @ExceptionHandler(BizException.class)
    public Result<Void> handleBiz(BizException e, HttpServletResponse response) {
        response.setStatus(e.getCode());          // 真实返回 401/403/404，便于前端和监控
        return Result.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidation(MethodArgumentNotValidException e, HttpServletResponse response) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getDefaultMessage() == null ? f.getField() : f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        return Result.error(400, message);
    }
}