package com.shepherd.boot.excel;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * 任务的对外投影。
 *
 * <p>刻意不含 ownerId（泄露他人标识）与 resultKey（泄露存储布局），只给可直接使用的下载链接。
 */
public record ExcelTaskVo(
        String id,
        String type,
        String status,
        String fileName,
        long total,
        long processed,
        @Nullable String downloadUrl,
        @Nullable String errorMessage,
        Instant createdAt,
        @Nullable Instant finishedAt) {

    public static ExcelTaskVo from(ExcelTask task, @Nullable String downloadUrl) {
        return new ExcelTaskVo(task.id(), task.type().name(), task.status().name(),
                task.fileName(), task.total(), task.processed(), downloadUrl,
                task.errorMessage(), task.createdAt(), task.finishedAt());
    }
}
