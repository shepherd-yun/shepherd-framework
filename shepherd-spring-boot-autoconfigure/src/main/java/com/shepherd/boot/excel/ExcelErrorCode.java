package com.shepherd.boot.excel;

import com.shepherd.boot.web.code.ErrorCode;

/**
 * Excel 导入导出错误码。
 *
 * <p>与 {@link com.shepherd.boot.storage.StorageErrorCode} 同样按 HTTP 语义分类而非按模块连续分段。
 * 归属不匹配的任务查询复用 {@link #TASK_NOT_FOUND} 而不返回 403——403 等于确认
 * 「该 id 存在但不属于你」，给了枚举探测的确认信号。
 */
public enum ExcelErrorCode implements ErrorCode {
    HEAD_MISMATCH(40004, "Excel 表头与模板不匹配"),
    UNSUPPORTED_FORMAT(40005, "不支持的文件格式"),
    EMPTY_FILE(40006, "文件为空或无数据行"),
    UNKNOWN_TEMPLATE_FIELD(40007, "模板下拉指定的字段名不存在"),
    TASK_NOT_FOUND(40402, "任务不存在"),
    TOO_MANY_ROWS(41301, "导入行数超出上限"),
    PARSE_FAILED(50005, "Excel 解析失败"),
    WRITE_FAILED(50006, "Excel 生成失败");

    private final int code;
    private final String message;

    ExcelErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
