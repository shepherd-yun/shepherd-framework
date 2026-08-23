package com.shepherd.boot.core.result;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;

@Getter
public class Result<T> {

    private final int code;
    private final String message;
    private final @Nullable T data;
    private final @Nullable String traceId;

    private Result(int code, String message, @Nullable T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.traceId = MDC.get("traceId");
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(CommonResultCode.SUCCESS.getCode(), CommonResultCode.SUCCESS.getMessage(), data);
    }

    public static Result<Void> success() {
        return new Result<>(CommonResultCode.SUCCESS.getCode(), CommonResultCode.SUCCESS.getMessage(), null);
    }

    public static <T> Result<T> fail(ErrorCode ec) {
        return new Result<>(ec.getCode(), ec.getMessage(), null);
    }

    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }
}
