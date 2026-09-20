package com.shepherd.boot.excel;

import cn.idev.excel.annotation.ExcelProperty;

/**
 * 错误行 Excel 的表头模型。
 *
 * <p>{@link ExcelRowError} 是 record，FastExcel 的写出依赖 setter/字段访问，
 * 因此单独给一个可写的投影类，顺带把表头写成用户看得懂的中文。
 */
public class ExcelErrorRow {

    @ExcelProperty("行号")
    private Integer rowNum;

    @ExcelProperty("列")
    private String column;

    @ExcelProperty("原因")
    private String message;

    public ExcelErrorRow() {
    }

    public ExcelErrorRow(ExcelRowError error) {
        this.rowNum = error.rowNum();
        this.column = error.column();
        this.message = error.message();
    }

    public Integer getRowNum() {
        return rowNum;
    }

    public void setRowNum(Integer rowNum) {
        this.rowNum = rowNum;
    }

    public String getColumn() {
        return column;
    }

    public void setColumn(String column) {
        this.column = column;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
