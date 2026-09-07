package com.shepherd.boot.web.code;

/**
 * Starter 占用的错误码：1xxxx，以及两个历史保留码 20001 / 20002。
 * <p>
 * 20001 / 20002 之所以留在 Starter，是因为 {@code JwtTokenProvider} 抛出它们，
 * 而前端 {@code AUTH_ERROR_CODES = [10001, 20001, 20002]} 已硬依赖这些数值。
 * 下游业务码从 2xxxx 起，须避开这两个保留码。
 */
public enum CommonResultCode implements ErrorCode {
    SUCCESS(0, "ok"),

    // 1xxxx 系统通用
    BAD_REQUEST(10000, "请求参数错误"),
    UNAUTHORIZED(10001, "未认证或登录已失效"),
    FORBIDDEN(10002, "无权限访问"),
    NOT_FOUND(10003, "资源不存在"),
    SYSTEM_ERROR(10004, "系统繁忙，请稍后再试"),

    // 保留码：认证凭证相关，前端据此登出
    TOKEN_INVALID(20001, "登录凭证无效"),
    TOKEN_EXPIRED(20002, "登录已过期，请重新登录");

    private final int code;
    private final String message;

    CommonResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override public int getCode() { return code; }
    @Override public String getMessage() { return message; }
}
