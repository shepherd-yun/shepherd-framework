package com.shepherd.boot.excel;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExcelErrorCodeTest {

    @Test
    void codes_are_classified_by_http_semantics_not_by_module() {
        // 表头/格式/空文件/模板字段名 都是调用方或上传内容的问题
        assertThat(ExcelErrorCode.HEAD_MISMATCH.getCode()).isEqualTo(40004);
        assertThat(ExcelErrorCode.UNSUPPORTED_FORMAT.getCode()).isEqualTo(40005);
        assertThat(ExcelErrorCode.EMPTY_FILE.getCode()).isEqualTo(40006);
        assertThat(ExcelErrorCode.UNKNOWN_TEMPLATE_FIELD.getCode()).isEqualTo(40007);
        // 任务不存在是 404 语义（归属不匹配也复用此码，不泄露存在性）
        assertThat(ExcelErrorCode.TASK_NOT_FOUND.getCode()).isEqualTo(40402);
        // 行数超限是 413 语义
        assertThat(ExcelErrorCode.TOO_MANY_ROWS.getCode()).isEqualTo(41301);
        // 解析/生成失败是服务端故障
        assertThat(ExcelErrorCode.PARSE_FAILED.getCode()).isEqualTo(50005);
        assertThat(ExcelErrorCode.WRITE_FAILED.getCode()).isEqualTo(50006);
    }

    @Test
    void every_code_reserves_the_starter_half_of_its_segment() {
        for (ExcelErrorCode rc : ExcelErrorCode.values()) {
            assertThat(rc.getCode() % 100)
                    .as("%s(%d) 低两位必须落在 00~49", rc.name(), rc.getCode())
                    .isBetween(0, 49);
        }
    }

    @Test
    void codes_do_not_collide_with_existing_starter_codes() {
        // 现有已占：40000-40003 / 40100-40102 / 40300 / 40400 / 40401 / 40500
        //          / 40900 / 41300 / 42900 / 50000-50004
        int[] taken = {40000, 40001, 40002, 40003, 40100, 40101, 40102, 40300,
                40400, 40401, 40500, 40900, 41300, 42900,
                50000, 50001, 50002, 50003, 50004};
        for (ExcelErrorCode rc : ExcelErrorCode.values()) {
            assertThat(taken)
                    .as("%s(%d) 与既有错误码撞号", rc.name(), rc.getCode())
                    .doesNotContain(rc.getCode());
        }
    }

    @Test
    void exception_carries_error_code_and_cause() {
        Throwable cause = new IllegalStateException("底层解析炸了");
        ExcelException ex = new ExcelException(ExcelErrorCode.PARSE_FAILED, "解析失败", cause);

        assertThat(ex.getErrorCode()).isEqualTo(ExcelErrorCode.PARSE_FAILED);
        assertThat(ex.getMessage()).isEqualTo("解析失败");
        // BizException 无 cause 构造器，必须 initCause，否则 POI 栈会丢
        assertThat(ex.getCause()).isSameAs(cause);
    }
}
