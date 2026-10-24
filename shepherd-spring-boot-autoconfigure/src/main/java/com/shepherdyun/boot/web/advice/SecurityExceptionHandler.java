package com.shepherdyun.boot.web.advice;

import com.shepherdyun.boot.web.code.CommonResultCode;
import com.shepherdyun.boot.web.vo.Result;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 仅在 classpath 上存在 Spring Security 时注册，见 ShepherdWebAutoConfiguration。
 * Order 高于 GlobalExceptionHandler，确保 AccessDeniedException 不被其兜底的
 * Exception 处理方法先行捕获。
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class SecurityExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    public Result<Void> handleAccessDenied(AccessDeniedException ex) {
        return Result.fail(CommonResultCode.FORBIDDEN);
    }
}
