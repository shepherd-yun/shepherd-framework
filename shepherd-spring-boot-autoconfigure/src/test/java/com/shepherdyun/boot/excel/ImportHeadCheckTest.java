package com.shepherdyun.boot.excel;

import cn.idev.excel.FastExcel;
import cn.idev.excel.annotation.ExcelProperty;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 导入表头校验。
 *
 * <p>FastExcel 对具名列是按表头文字找列的，找不到就把该字段<b>静默丢掉</b>——整列读成 null，
 * 不报任何错。用户拿旧模板、或拿错模块的模板导入，结果就是「导入成功」但某列全空。
 * 所以按名字匹配的列缺一个就拒绝整份文件，而不是事后逐行报「xx 不能为空」让用户猜原因。
 */
class ImportHeadCheckTest {

    public static class PortDto {
        @ExcelProperty("港口五字码")
        private String code;

        @ExcelProperty("中文名称")
        private String name;

        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    /** 纯按列位读取、没写表头名的 DTO：无从校验，也不该被误拒。 */
    public static class IndexOnlyDto {
        @ExcelProperty(index = 0)
        private String first;

        public String getFirst() { return first; }
        public void setFirst(String first) { this.first = first; }
    }

    /** 同时指定列位和表头名：该列位上的表头必须是这个名字。 */
    public static class IndexedNamedDto {
        @ExcelProperty(value = "港口五字码", index = 0)
        private String code;

        @ExcelProperty(value = "中文名称", index = 1)
        private String name;

        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    private static ByteArrayInputStream sheet(List<String> head, List<List<Object>> rows) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        FastExcel.write(out)
                .head(head.stream().map(List::of).toList())
                .sheet("Sheet1")
                .doWrite(rows);
        return new ByteArrayInputStream(out.toByteArray());
    }

    private final ExcelImporter importer = new ExcelImporter(new ShepherdExcelProperties(), null);

    @Test
    void reordered_columns_are_accepted_and_mapped_by_name() {
        List<PortDto> consumed = new ArrayList<>();

        importer.read(sheet(List.of("中文名称", "港口五字码"), List.of(List.of("上海港", "CNSHA"))),
                PortDto.class, consumed::addAll);

        assertThat(consumed).singleElement().satisfies(p -> {
            assertThat(p.getCode()).isEqualTo("CNSHA");
            assertThat(p.getName()).isEqualTo("上海港");
        });
    }

    @Test
    void missing_named_column_rejects_the_whole_file_before_any_row_is_consumed() {
        List<PortDto> consumed = new ArrayList<>();

        assertThatThrownBy(() -> importer.read(
                sheet(List.of("港口五字码"), List.of(List.of("CNSHA"))), PortDto.class, consumed::addAll))
                .isInstanceOf(ExcelException.class)
                .hasMessageContaining("缺少列：中文名称")
                .hasMessageContaining("请下载最新模板")
                .extracting(e -> ((ExcelException) e).getErrorCode())
                .isEqualTo(ExcelErrorCode.HEAD_MISMATCH);
        assertThat(consumed).isEmpty();
    }

    /** 旧模板改过列名：新名缺失、旧名多出，两边都报出来，用户一眼能看出是模板版本问题。 */
    @Test
    void renamed_column_reports_both_missing_and_unexpected_names() {
        assertThatThrownBy(() -> importer.read(
                sheet(List.of("港口五字码", "中文名"), List.of(List.of("CNSHA", "上海港"))),
                PortDto.class, b -> { }))
                .isInstanceOf(ExcelException.class)
                .hasMessageContaining("缺少列：中文名称")
                .hasMessageContaining("多出列：中文名");
    }

    @Test
    void unexpected_extra_column_is_rejected() {
        assertThatThrownBy(() -> importer.read(
                sheet(List.of("港口五字码", "中文名称", "备注"), List.of(List.of("CNSHA", "上海港", "x"))),
                PortDto.class, b -> { }))
                .isInstanceOf(ExcelException.class)
                .hasMessageContaining("多出列：备注");
    }

    @Test
    void surrounding_whitespace_in_header_is_ignored() {
        List<PortDto> consumed = new ArrayList<>();

        importer.read(sheet(List.of(" 港口五字码 ", "中文名称"), List.of(List.of("CNSHA", "上海港"))),
                PortDto.class, consumed::addAll);

        assertThat(consumed).singleElement().extracting(PortDto::getCode).isEqualTo("CNSHA");
    }

    /** 表头错了就报表头错，而不是被「无数据行」盖掉——用户该做的是换模板，不是补数据。 */
    @Test
    void wrong_header_wins_over_empty_file() {
        assertThatThrownBy(() -> importer.read(sheet(List.of("随便什么"), List.of()), PortDto.class, b -> { }))
                .isInstanceOf(ExcelException.class)
                .extracting(e -> ((ExcelException) e).getErrorCode())
                .isEqualTo(ExcelErrorCode.HEAD_MISMATCH);
    }

    @Test
    void index_only_dto_is_not_header_checked() {
        List<IndexOnlyDto> consumed = new ArrayList<>();

        importer.read(sheet(List.of("任意表头", "另一列"), List.of(List.of("a", "b"))),
                IndexOnlyDto.class, consumed::addAll);

        assertThat(consumed).singleElement().extracting(IndexOnlyDto::getFirst).isEqualTo("a");
    }

    @Test
    void indexed_named_column_must_carry_its_name_at_its_position() {
        assertThatThrownBy(() -> importer.read(
                sheet(List.of("中文名称", "港口五字码"), List.of(List.of("上海港", "CNSHA"))),
                IndexedNamedDto.class, b -> { }))
                .isInstanceOf(ExcelException.class)
                .hasMessageContaining("第 1 列应为「港口五字码」，实为「中文名称」");
    }
}
