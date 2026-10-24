package com.shepherdyun.boot.storage;

import com.shepherdyun.boot.web.code.ErrorCode;

/**
 * 对象存储错误码。
 *
 * <p>按 HTTP 语义分类而非按模块连续分段——这是有意的取舍：错误码首要服务于
 * 「前端该怎么处理」，模块归属由 message 与 traceId 在日志里追。
 * 因此这 6 个码分散在 400 / 404 / 500 三段。
 */
public enum StorageErrorCode implements ErrorCode {
    INVALID_KEY(40003, "对象 key 非法"),
    OBJECT_NOT_FOUND(40401, "对象不存在"),
    UPLOAD_FAILED(50001, "对象上传失败"),
    DOWNLOAD_FAILED(50002, "对象下载失败"),
    DELETE_FAILED(50003, "对象删除失败"),
    PRESIGN_FAILED(50004, "预签名 URL 生成失败");

    private final int code;
    private final String message;

    StorageErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override public int getCode() { return code; }
    @Override public String getMessage() { return message; }
}
