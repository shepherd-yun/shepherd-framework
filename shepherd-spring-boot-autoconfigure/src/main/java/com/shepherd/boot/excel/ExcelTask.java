package com.shepherd.boot.excel;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * 导入/导出任务。导出任务的 resultKey 指向导出文件，导入任务的指向错误行 Excel——二者同构。
 *
 * @param total 未知时为 -1
 */
public record ExcelTask(
        String id,
        ExcelTaskType type,
        ExcelTaskStatus status,
        String ownerId,
        String fileName,
        long total,
        long processed,
        @Nullable String resultKey,
        @Nullable String errorMessage,
        Instant createdAt,
        @Nullable Instant finishedAt) {

    public static ExcelTask pending(String id, ExcelTaskType type, String ownerId, String fileName) {
        return new ExcelTask(id, type, ExcelTaskStatus.PENDING, ownerId, fileName,
                -1, 0, null, null, Instant.now(), null);
    }

    public ExcelTask withProgress(long processed, long total) {
        return new ExcelTask(id, type, ExcelTaskStatus.RUNNING, ownerId, fileName,
                total, processed, resultKey, errorMessage, createdAt, finishedAt);
    }

    public ExcelTask succeeded(@Nullable String resultKey) {
        return new ExcelTask(id, type, ExcelTaskStatus.SUCCEEDED, ownerId, fileName,
                total, processed, resultKey, null, createdAt, Instant.now());
    }

    public ExcelTask failed(String errorMessage) {
        return new ExcelTask(id, type, ExcelTaskStatus.FAILED, ownerId, fileName,
                total, processed, resultKey, errorMessage, createdAt, Instant.now());
    }
}
