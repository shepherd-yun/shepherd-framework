package com.shepherd.boot.web.advice;

import com.shepherd.boot.web.code.CommonResultCode;
import com.shepherd.boot.web.vo.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 方法级校验（@Validated 标在类上、约束标在方法参数上）失败的翻译。
 *
 * <p>仅在 classpath 上存在 jakarta.validation 时注册，见 ShepherdWebAutoConfiguration。
 * 拆成独立类的理由与 {@link SecurityExceptionHandler} 相同：含
 * ConstraintViolationException 形参的方法若无条件注册，下游未引 validation 时
 * 会在 advice 解析阶段抛 NoClassDefFoundError 导致启动失败。
 *
 * <p>Order 高于 GlobalExceptionHandler，确保不被其兜底的 Exception 方法先行捕获。
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class ValidationExceptionHandler {

    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolation(ConstraintViolationException ex,
                                                  HttpServletRequest request) {
        String msg = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining("; "));
        if (msg.isBlank()) {
            msg = CommonResultCode.BAD_REQUEST.getMessage();
        }
        log.warn("方法级校验失败 {} {} -> {}", request.getMethod(), request.getRequestURI(), msg);
        return Result.fail(CommonResultCode.BAD_REQUEST.getCode(), msg);
    }
}
