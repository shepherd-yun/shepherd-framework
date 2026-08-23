package com.shepherd.boot.core.exception;

import com.shepherd.boot.core.result.CommonResultCode;
import com.shepherd.boot.core.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 通用异常翻译。刻意不引用任何 Spring Security 类型：
 * 下游若未引入 security，含 AccessDeniedException 形参的处理方法会在
 * advice 解析阶段抛 NoClassDefFoundError 导致启动失败。
 * Security 相关的处理见 {@link SecurityExceptionHandler}。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public Result<Void> handleBiz(BizException ex) {
        return Result.fail(ex.getErrorCode().getCode(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return Result.fail(CommonResultCode.BAD_REQUEST.getCode(), msg);
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return Result.fail(CommonResultCode.SYSTEM_ERROR);
    }
}
