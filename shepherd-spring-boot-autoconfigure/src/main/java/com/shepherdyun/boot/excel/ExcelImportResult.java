package com.shepherdyun.boot.excel;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * 导入结果。
 *
 * @param errors          只保留前 {@code inlineLimit} 条；全量在 errorFileKey 指向的 Excel 里
 * @param errorsTruncated errors 是否被截断
 * @param errorFileKey    错误行 Excel 的 object key；未截断时为 null
 */
public record ExcelImportResult<T>(
        long total,
        long succeeded,
        long failed,
        List<ExcelRowError> errors,
        boolean errorsTruncated,
        @Nullable String errorFileKey) {

    public static <T> ExcelImportResult<T> of(long total, long succeeded,
                                              List<ExcelRowError> allErrors,
                                              int inlineLimit,
                                              @Nullable String errorFileKey) {
        boolean truncated = allErrors.size() > inlineLimit;
        List<ExcelRowError> inline = truncated
                ? List.copyOf(allErrors.subList(0, inlineLimit))
                : List.copyOf(allErrors);
        return new ExcelImportResult<>(total, succeeded, allErrors.size(),
                inline, truncated, errorFileKey);
    }
}
