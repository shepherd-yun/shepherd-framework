package com.shepherdyun.boot.excel;

/**
 * 一条导入行的校验/入库失败。
 *
 * @param rowNum  1-based 的 Excel 行号（表头第 1 行，首条数据第 2 行），直接对应用户看到的行号
 * @param column  出错的字段名；整行失败（如回调抛异常）时为空串
 * @param message 失败原因
 */
public record ExcelRowError(int rowNum, String column, String message) {
}
