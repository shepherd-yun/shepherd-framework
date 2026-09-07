package com.shepherd.boot.storage;

import com.shepherd.boot.web.code.ErrorCode;

/** 对象存储错误码，占用 Starter 保留段 10100~10199。 */
public enum StorageErrorCode implements ErrorCode {
    INVALID_KEY(10100, "对象 key 非法"),
    UPLOAD_FAILED(10101, "对象上传失败"),
    DOWNLOAD_FAILED(10102, "对象下载失败"),
    DELETE_FAILED(10103, "对象删除失败"),
    PRESIGN_FAILED(10104, "预签名 URL 生成失败"),
    OBJECT_NOT_FOUND(10105, "对象不存在");

    private final int code;
    private final String message;

    StorageErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override public int getCode() { return code; }
    @Override public String getMessage() { return message; }
}
