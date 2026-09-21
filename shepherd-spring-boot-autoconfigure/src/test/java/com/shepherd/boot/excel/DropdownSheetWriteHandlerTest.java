package com.shepherd.boot.excel;

import cn.idev.excel.FastExcel;
import cn.idev.excel.annotation.ExcelProperty;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.SheetVisibility;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class DropdownSheetWriteHandlerTest {

    static class Head {
        @ExcelProperty("姓名") private String name;
        @ExcelProperty("部门") private String deptName;
    }

    /** 走完整写入链路拿到真实的 xlsx 字节，再用 POI 读回来断言——只有这样才能验到 Excel 实际看到的东西。 */
    private Workbook writeTemplate(Map<String, List<String>> dropdowns, int rows) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        FastExcel.write(out, Head.class)
                .registerWriteHandler(new DropdownSheetWriteHandler(Head.class, dropdowns, rows, "__options"))
                .sheet("模板")
                .doWrite(List.of());
        return new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()));
    }

    @Test
    void short_options_use_inline_explicit_list() throws Exception {
        Workbook wb = writeTemplate(Map.of("deptName", List.of("研发部", "市场部")), 500);

        assertThat(wb.getNumberOfSheets()).as("短选项不应产生隐藏 sheet").isEqualTo(1);

        List<? extends DataValidation> validations = wb.getSheetAt(0).getDataValidations();
        assertThat(validations).hasSize(1);
        DataValidationConstraint c = validations.get(0).getValidationConstraint();
        assertThat(c.getValidationType()).isEqualTo(DataValidationConstraint.ValidationType.LIST);
        assertThat(c.getExplicitListValues()).containsExactly("研发部", "市场部");
    }

    @Test
    void options_longer_than_255_chars_fall_back_to_hidden_sheet() throws Exception {
        // 40 个 8 字符选项 → 拼接后约 360 字符，超过 Excel 的 255 上限
        List<String> many = IntStream.range(0, 40).mapToObj(i -> "部门名称%02d".formatted(i)).toList();
        assertThat(String.join(",", many).length()).isGreaterThan(255);

        Workbook wb = writeTemplate(Map.of("deptName", many), 500);

        Sheet hidden = wb.getSheet("__options");
        assertThat(hidden).as("超长选项必须落隐藏 sheet").isNotNull();
        assertThat(wb.getSheetVisibility(wb.getSheetIndex(hidden))).isEqualTo(SheetVisibility.HIDDEN);

        List<String> written = new ArrayList<>();
        for (int i = 0; i < many.size(); i++) {
            written.add(hidden.getRow(i).getCell(0).getStringCellValue());
        }
        assertThat(written).isEqualTo(many);

        DataValidationConstraint c = wb.getSheetAt(0).getDataValidations().get(0).getValidationConstraint();
        assertThat(c.getFormula1()).isEqualTo("__options!$A$1:$A$40");
    }

    @Test
    void option_containing_comma_forces_hidden_sheet_even_when_short() throws Exception {
        // 内联格式是逗号分隔的，"北京,朝阳" 会被 Excel 拆成两项
        Workbook wb = writeTemplate(Map.of("deptName", List.of("北京,朝阳", "上海")), 500);

        assertThat(wb.getSheet("__options")).isNotNull();
        DataValidationConstraint c = wb.getSheetAt(0).getDataValidations().get(0).getValidationConstraint();
        assertThat(c.getFormula1()).isEqualTo("__options!$A$1:$A$2");
    }

    @Test
    void validation_covers_the_requested_row_range_under_the_header() throws Exception {
        Workbook wb = writeTemplate(Map.of("deptName", List.of("研发部")), 10);

        var regions = wb.getSheetAt(0).getDataValidations().get(0).getRegions().getCellRangeAddresses();
        assertThat(regions).hasSize(1);
        // 第 0 行是表头，数据从第 1 行起，共 10 行 → 1..10
        assertThat(regions[0].getFirstRow()).isEqualTo(1);
        assertThat(regions[0].getLastRow()).isEqualTo(10);
        // deptName 是第 2 列（0-based 索引 1）
        assertThat(regions[0].getFirstColumn()).isEqualTo(1);
        assertThat(regions[0].getLastColumn()).isEqualTo(1);
    }

    @Test
    void multiple_dropdowns_each_get_their_own_hidden_column() throws Exception {
        List<String> many = IntStream.range(0, 40).mapToObj(i -> "选项名称%02d".formatted(i)).toList();
        Workbook wb = writeTemplate(Map.of("name", many, "deptName", many), 500);

        Sheet hidden = wb.getSheet("__options");
        assertThat(hidden.getRow(0).getLastCellNum()).as("两个字段各占一列").isEqualTo((short) 2);
        assertThat(wb.getSheetAt(0).getDataValidations()).hasSize(2);
    }
}
