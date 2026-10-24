package com.shepherdyun.boot.excel;

import cn.idev.excel.annotation.ExcelProperty;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HeadColumnsTest {

    static class PlainHead {
        @ExcelProperty("姓名") private String name;
        @ExcelProperty("部门") private String deptName;
        @ExcelProperty("状态") private String status;
    }

    static class ExplicitIndexHead {
        @ExcelProperty(value = "状态", index = 2) private String status;
        @ExcelProperty("姓名") private String name;
        @ExcelProperty("部门") private String deptName;
    }

    static class NoAnnotationHead {
        @ExcelProperty("姓名") private String name;
        private String ignored;
    }

    @Test
    void plain_head_maps_fields_in_declaration_order() {
        assertThat(HeadColumns.indexOf(PlainHead.class, "name")).isZero();
        assertThat(HeadColumns.indexOf(PlainHead.class, "deptName")).isEqualTo(1);
        assertThat(HeadColumns.indexOf(PlainHead.class, "status")).isEqualTo(2);
    }

    @Test
    void explicit_index_wins_and_others_fill_remaining_slots() {
        // status 钉死在 2；name、deptName 按声明顺序填 0、1
        assertThat(HeadColumns.indexOf(ExplicitIndexHead.class, "status")).isEqualTo(2);
        assertThat(HeadColumns.indexOf(ExplicitIndexHead.class, "name")).isZero();
        assertThat(HeadColumns.indexOf(ExplicitIndexHead.class, "deptName")).isEqualTo(1);
    }

    @Test
    void unknown_field_fails_fast_rather_than_producing_a_broken_template() {
        assertThatThrownBy(() -> HeadColumns.indexOf(PlainHead.class, "notThere"))
                .isInstanceOf(ExcelException.class)
                .hasMessageContaining("notThere")
                .hasMessageContaining("PlainHead");
    }

    @Test
    void field_without_excel_property_is_not_a_column() {
        assertThatThrownBy(() -> HeadColumns.indexOf(NoAnnotationHead.class, "ignored"))
                .isInstanceOf(ExcelException.class);
    }
}
