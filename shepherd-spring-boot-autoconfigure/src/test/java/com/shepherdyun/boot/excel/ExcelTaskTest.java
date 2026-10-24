package com.shepherdyun.boot.excel;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ExcelTaskTest {

    @Test
    void pending_task_starts_with_unknown_total_and_no_result() {
        ExcelTask task = ExcelTask.pending("EX20260913001", ExcelTaskType.EXPORT, "admin", "订单.xlsx");

        assertThat(task.status()).isEqualTo(ExcelTaskStatus.PENDING);
        assertThat(task.total()).as("总数未知时为 -1").isEqualTo(-1);
        assertThat(task.processed()).isZero();
        assertThat(task.resultKey()).isNull();
        assertThat(task.finishedAt()).isNull();
        assertThat(task.createdAt()).isNotNull();
    }

    @Test
    void succeeded_stamps_result_key_and_finish_time() {
        ExcelTask task = ExcelTask.pending("EX1", ExcelTaskType.EXPORT, "admin", "订单.xlsx")
                .succeeded("excel/export/EX1.xlsx");

        assertThat(task.status()).isEqualTo(ExcelTaskStatus.SUCCEEDED);
        assertThat(task.resultKey()).isEqualTo("excel/export/EX1.xlsx");
        assertThat(task.finishedAt()).isNotNull();
        assertThat(task.errorMessage()).isNull();
    }

    @Test
    void failed_stamps_message_and_finish_time() {
        ExcelTask task = ExcelTask.pending("EX1", ExcelTaskType.IMPORT, "admin", "用户.xlsx")
                .failed("解析失败");

        assertThat(task.status()).isEqualTo(ExcelTaskStatus.FAILED);
        assertThat(task.errorMessage()).isEqualTo("解析失败");
        assertThat(task.finishedAt()).isNotNull();
    }

    @Test
    void vo_never_exposes_owner_id_or_raw_object_key() {
        ExcelTask task = ExcelTask.pending("EX1", ExcelTaskType.EXPORT, "alice", "订单.xlsx")
                .succeeded("excel/export/EX1.xlsx");

        ExcelTaskVo vo = ExcelTaskVo.from(task, "https://cdn.example.com/excel/export/EX1.xlsx");

        assertThat(vo.id()).isEqualTo("EX1");
        assertThat(vo.downloadUrl()).isEqualTo("https://cdn.example.com/excel/export/EX1.xlsx");
        // ownerId 不在 VO 的任何字段里——泄露他人标识
        assertThat(vo.toString()).doesNotContain("alice");
        // resultKey 也不外露（downloadUrl 里出现同名路径不算外露，故按 record 组件名断言）
        assertThat(ExcelTaskVo.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .doesNotContain("ownerId", "resultKey");
    }

    @Test
    void progress_updates_keep_identity_and_owner() {
        ExcelTask task = ExcelTask.pending("EX1", ExcelTaskType.EXPORT, "admin", "订单.xlsx")
                .withProgress(100, 5000);

        assertThat(task.processed()).isEqualTo(100);
        assertThat(task.total()).isEqualTo(5000);
        assertThat(task.id()).isEqualTo("EX1");
        assertThat(task.ownerId()).isEqualTo("admin");
        assertThat(task.status()).isEqualTo(ExcelTaskStatus.RUNNING);
    }

    @Test
    void created_at_is_preserved_across_transitions() {
        ExcelTask pending = ExcelTask.pending("EX1", ExcelTaskType.EXPORT, "admin", "订单.xlsx");
        Instant created = pending.createdAt();

        assertThat(pending.withProgress(1, 10).succeeded("k").createdAt()).isEqualTo(created);
    }
}
