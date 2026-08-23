package com.shepherd.boot.storage;

import com.shepherd.boot.core.exception.BizException;

/**
 * 继承 BizException，使存储异常自动落入统一 Result 契约。
 * BizException 没有携带 cause 的构造器，故用 initCause 挂原因。
 */
public class StorageException extends BizException {

    public StorageException(StorageErrorCode code, String message) {
        super(code, message);
    }

    public StorageException(StorageErrorCode code, String message, Throwable cause) {
        super(code, message);
        initCause(cause);
    }
}
