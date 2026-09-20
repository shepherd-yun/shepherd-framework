package com.shepherd.boot.web.code;

/**
 * Starter 通用错误码。
 *
 * <p>编码规则：{@code <3 位 HTTP 状态><2 位序号>}，共 5 位。成功码恒为 {@code 0}。
 * 每段内低两位 {@code 00~49} 归 Starter、{@code 50~99} 归下游业务，
 * 由 {@code CommonResultCodeTest} 与下游的对称测试共同守卫。
 *
 * <p><b>HTTP 响应状态恒为 200</b>——码里的 4xx/5xx 只是语义前缀，不是真实响应状态。
 *
 * <p>{@code 40100~40149} 是「凭证失效需登出」区间，前端据此清理登录态；
 * 业务侧的登录流程失败（如密码错误）应使用 {@code 40150+}，不触发登出。
 */
public enum CommonResultCode implements ErrorCode {

    SUCCESS(0, "ok"),

    // 400xx 参数 / 请求错误
    BAD_REQUEST(40000, "请求参数错误"),
    BODY_UNREADABLE(40001, "请求体格式错误"),
    PARAM_TYPE_MISMATCH(40002, "请求参数类型不匹配"),

    // 401xx 未认证（Starter 段 = 凭证失效，前端应登出）
    UNAUTHORIZED(40100, "未认证或登录已失效"),
    TOKEN_INVALID(40101, "登录凭证无效"),
    TOKEN_EXPIRED(40102, "登录已过期，请重新登录"),

    // 403xx 无权限
    FORBIDDEN(40300, "无权限访问"),

    // 404xx 资源不存在
    NOT_FOUND(40400, "资源不存在"),

    // 405xx 方法不支持
    METHOD_NOT_ALLOWED(40500, "请求方法不支持"),

    // 409xx 状态冲突
    REPEAT_SUBMIT(40900, "请勿重复提交"),

    // 413xx 超出限制
    PAYLOAD_TOO_LARGE(41300, "上传文件过大"),

    // 429xx 限流
    RATE_LIMITED(42900, "请求过于频繁，请稍后再试"),

    // 500xx 服务端错误
    SYSTEM_ERROR(50000, "系统繁忙，请稍后再试");

    private final int code;
    private final String message;

    CommonResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override public int getCode() { return code; }
    @Override public String getMessage() { return message; }
}
