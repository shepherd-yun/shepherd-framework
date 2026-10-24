package com.shepherdyun.boot.excel;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class ExcelImportResultTest {

    private static List<ExcelRowError> errors(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> new ExcelRowError(i + 2, "name", "姓名不能为空"))
                .toList();
    }

    @Test
    void counts_stay_consistent() {
        ExcelImportResult<String> r = ExcelImportResult.of(10, 7, errors(3), 200, null);

        assertThat(r.total()).isEqualTo(10);
        assertThat(r.succeeded()).isEqualTo(7);
        assertThat(r.failed()).isEqualTo(3);
        assertThat(r.errors()).hasSize(3);
        assertThat(r.errorsTruncated()).isFalse();
        assertThat(r.errorFileKey()).isNull();
    }

    @Test
    void errors_are_truncated_beyond_the_inline_limit() {
        ExcelImportResult<String> r = ExcelImportResult.of(500, 300, errors(200), 50, "excel/error/abc.xlsx");

        assertThat(r.failed()).as("failed 记全量，不受截断影响").isEqualTo(200);
        assertThat(r.errors()).as("errors 只保留前 inlineLimit 条").hasSize(50);
        assertThat(r.errorsTruncated()).isTrue();
        assertThat(r.errorFileKey()).isEqualTo("excel/error/abc.xlsx");
    }

    @Test
    void row_num_is_the_one_based_excel_row_so_users_can_locate_it() {
        // 表头是第 1 行，首条数据是第 2 行——直接对应用户在 Excel 里看到的行号
        ExcelRowError e = new ExcelRowError(2, "name", "姓名不能为空");

        assertThat(e.rowNum()).isEqualTo(2);
        assertThat(e.column()).isEqualTo("name");
        assertThat(e.message()).isEqualTo("姓名不能为空");
    }

    @Test
    void errors_list_is_immutable() {
        ExcelImportResult<String> r = ExcelImportResult.of(1, 0, errors(1), 200, null);

        assertThat(r.errors()).isUnmodifiable();
    }
}
