package com.shepherd.boot.web.advice;

import com.shepherd.boot.web.code.CommonResultCode;
import com.shepherd.boot.web.exception.BizException;
import com.shepherd.boot.web.vo.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 通用异常翻译。
 *
 * <p><b>刻意不引用任何 Spring Security 或 jakarta.validation 类型</b>：
 * 下游若未引入对应依赖，含这些形参的处理方法会在 advice 解析阶段抛
 * NoClassDefFoundError 导致启动失败。它们分别由 {@link SecurityExceptionHandler}
 * 与 {@link ValidationExceptionHandler} 处理，两者都由 @ConditionalOnClass 守卫。
 *
 * <p>日志级别的原则：调用方能自行修正的（4xx 语义）打 warn，
 * 只有真正未知的异常才打 error 带栈——否则 error 日志会被 404 噪声淹没。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public Result<Void> handleBiz(BizException ex, HttpServletRequest request) {
        log.warn("业务异常 {} {} -> [{}] {}",
                request.getMethod(), request.getRequestURI(), ex.getErrorCode().getCode(), ex.getMessage());
        return Result.fail(ex.getErrorCode().getCode(), ex.getMessage());
    }

    /**
     * MethodArgumentNotValidException 是 BindException 的子类（已对 spring-web 7.0.8 核实），
     * 所以这一个方法同时覆盖 @RequestBody 校验失败与 @ModelAttribute 查询对象绑定失败。
     * 原先只注册子类，导致后者漏网落进兜底分支。
     */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBind(BindException ex, HttpServletRequest request) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        if (msg.isBlank()) {
            msg = CommonResultCode.BAD_REQUEST.getMessage();
        }
        log.warn("参数绑定失败 {} {} -> {}", request.getMethod(), request.getRequestURI(), msg);
        return Result.fail(CommonResultCode.BAD_REQUEST.getCode(), msg);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException ex,
                                           HttpServletRequest request) {
        String msg = "缺少必需参数：" + ex.getParameterName();
        log.warn("{} {} -> {}", request.getMethod(), request.getRequestURI(), msg);
        return Result.fail(CommonResultCode.BAD_REQUEST.getCode(), msg);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                           HttpServletRequest request) {
        String msg = "参数 " + ex.getName() + " 类型不正确";
        log.warn("{} {} -> {}", request.getMethod(), request.getRequestURI(), msg);
        return Result.fail(CommonResultCode.PARAM_TYPE_MISMATCH.getCode(), msg);
    }

    /** 请求体不是合法 JSON，或无法反序列化成目标类型。异常 message 含内部类型信息，不外泄。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleUnreadableBody(HttpMessageNotReadableException ex,
                                             HttpServletRequest request) {
        log.warn("请求体无法解析 {} {} -> {}",
                request.getMethod(), request.getRequestURI(), ex.getMessage());
        return Result.fail(CommonResultCode.BODY_UNREADABLE);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                 HttpServletRequest request) {
        log.warn("方法不支持 {} {} -> 支持 {}",
                request.getMethod(), request.getRequestURI(), ex.getSupportedHttpMethods());
        return Result.fail(CommonResultCode.METHOD_NOT_ALLOWED);
    }

    /**
     * 静态资源 / 无匹配 handler。NoResourceFoundException 是 ServletException 子类，
     * 不单独注册就会落进兜底分支被报成「系统繁忙」并刷 error 栈——这是原实现的真实缺陷。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public Result<Void> handleNotFound(NoResourceFoundException ex, HttpServletRequest request) {
        log.warn("资源不存在 {} {}", request.getMethod(), request.getRequestURI());
        return Result.fail(CommonResultCode.NOT_FOUND);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<Void> handleTooLarge(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        log.warn("上传超限 {} {} -> 上限 {}",
                request.getMethod(), request.getRequestURI(), ex.getMaxUploadSize());
        return Result.fail(CommonResultCode.PAYLOAD_TOO_LARGE);
    }

    /** 兜底。到这里的都是真正未预期的异常，打 error 带栈；对外只回固定文案，不泄露内部信息。 */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("未处理异常 {} {}", request.getMethod(), request.getRequestURI(), ex);
        return Result.fail(CommonResultCode.SYSTEM_ERROR);
    }
}
