package com.shepherd.boot.excel;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BatchReadListenerTest {

    /** 传 null 的 AnalysisContext：本类的逻辑不读 context，构造真实 context 只会增加噪音。 */
    private static final cn.idev.excel.context.AnalysisContext NO_CONTEXT = null;

    @Test
    void flushes_when_batch_is_full_and_again_at_the_end() {
        List<List<String>> batches = new ArrayList<>();
        BatchReadListener<String> listener =
                new BatchReadListener<>(batch -> batches.add(List.copyOf(batch)), null, 2, 100);

        listener.invoke("a", NO_CONTEXT);
        listener.invoke("b", NO_CONTEXT);
        assertThat(batches).as("满 2 条即冲刷").hasSize(1);

        listener.invoke("c", NO_CONTEXT);
        listener.doAfterAllAnalysed(NO_CONTEXT);

        assertThat(batches).containsExactly(List.of("a", "b"), List.of("c"));
        assertThat(listener.total()).isEqualTo(3);
        assertThat(listener.succeeded()).isEqualTo(3);
        assertThat(listener.errors()).isEmpty();
    }

    @Test
    void consumer_failure_marks_the_whole_batch_failed_but_parsing_continues() {
        List<List<String>> ok = new ArrayList<>();
        BatchReadListener<String> listener = new BatchReadListener<>(batch -> {
            if (batch.contains("bad")) {
                throw new IllegalStateException("写库唯一键冲突");
            }
            ok.add(List.copyOf(batch));
        }, null, 2, 100);

        listener.invoke("bad", NO_CONTEXT);
        listener.invoke("x", NO_CONTEXT);   // 第 1 批炸
        listener.invoke("y", NO_CONTEXT);
        listener.invoke("z", NO_CONTEXT);   // 第 2 批成功
        listener.doAfterAllAnalysed(NO_CONTEXT);

        assertThat(ok).as("解析未中断，后续批次照常入库").containsExactly(List.of("y", "z"));
        assertThat(listener.total()).isEqualTo(4);
        assertThat(listener.succeeded()).isEqualTo(2);
        assertThat(listener.errors()).hasSize(2);
        assertThat(listener.errors()).allSatisfy(e ->
                assertThat(e.message()).contains("写库唯一键冲突"));
        // 第 1 批是第 1、2 条数据 → Excel 行号 2、3
        assertThat(listener.errors()).extracting(ExcelRowError::rowNum).containsExactly(2, 3);
    }

    @Test
    void exceeding_max_rows_aborts_with_too_many_rows() {
        BatchReadListener<String> listener =
                new BatchReadListener<>(batch -> { }, null, 100, 2);

        listener.invoke("a", NO_CONTEXT);
        listener.invoke("b", NO_CONTEXT);

        assertThatThrownBy(() -> listener.invoke("c", NO_CONTEXT))
                .isInstanceOf(ExcelException.class)
                .hasFieldOrPropertyWithValue("errorCode", ExcelErrorCode.TOO_MANY_ROWS)
                .hasMessageContaining("2");
    }

    @Test
    void empty_input_flushes_nothing() {
        List<List<String>> batches = new ArrayList<>();
        BatchReadListener<String> listener =
                new BatchReadListener<>(batch -> batches.add(List.copyOf(batch)), null, 2, 100);

        listener.doAfterAllAnalysed(NO_CONTEXT);

        assertThat(batches).isEmpty();
        assertThat(listener.total()).isZero();
    }
}
