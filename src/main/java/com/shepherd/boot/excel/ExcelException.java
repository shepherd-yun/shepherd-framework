package com.shepherd.boot.excel;

import com.shepherd.boot.web.exception.BizException;

/**
 * 继承 BizException，使 Excel 异常自动落入 GlobalExceptionHandler.handleBiz 的统一 Result 契约。
 * BizException 没有携带 cause 的构造器，故用 initCause 挂原因——否则 POI / FastExcel
 * 的底层异常栈会在转换时丢失，解析失败将无法排查。
 */
public class ExcelException extends BizException {

    public ExcelException(ExcelErrorCode code, String message) {
        super(code, message);
    }

    public ExcelException(ExcelErrorCode code, String message, Throwable cause) {
        super(code, message);
        initCause(cause);
    }
}
