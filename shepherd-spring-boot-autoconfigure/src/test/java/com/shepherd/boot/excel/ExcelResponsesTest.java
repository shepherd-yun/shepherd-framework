package com.shepherd.boot.excel;

import cn.idev.excel.annotation.ExcelProperty;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExcelResponsesTest {

    static class Row {
        @ExcelProperty("姓名") private String name;
        @ExcelProperty("部门") private String deptName;

        Row() {
        }

        Row(String name, String deptName) {
            this.name = name;
            this.deptName = deptName;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDeptName() {
            return deptName;
        }

        public void setDeptName(String deptName) {
            this.deptName = deptName;
        }
    }

    @Test
    void sets_xlsx_content_type_and_rfc5987_encoded_chinese_filename() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        ExcelResponses.write(response, "用户列表.xlsx", Row.class, List.of(new Row("张三", "研发部")));

        assertThat(response.getContentType())
                .isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        // RFC 5987：不能出现裸中文，且 URLEncoder 的 "+" 必须换成 %20
        String disposition = response.getHeader("Content-Disposition");
        assertThat(disposition).startsWith("attachment;filename*=utf-8''");
        assertThat(disposition).doesNotContain("用户");
        assertThat(disposition).doesNotContain("+");
        assertThat(disposition).contains("%E7%94%A8%E6%88%B7");
    }

    @Test
    void writes_a_readable_workbook_with_header_and_data() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        ExcelResponses.write(response, "用户列表.xlsx", Row.class,
                List.of(new Row("张三", "研发部"), new Row("李四", "市场部")));

        Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()));
        assertThat(wb.getSheetAt(0).getRow(0).getCell(0).getStringCellValue()).isEqualTo("姓名");
        assertThat(wb.getSheetAt(0).getRow(1).getCell(0).getStringCellValue()).isEqualTo("张三");
        assertThat(wb.getSheetAt(0).getRow(2).getCell(1).getStringCellValue()).isEqualTo("市场部");
    }

    @Test
    void write_stream_pages_until_fetcher_returns_empty() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        ExcelResponses.writeStream(response, "订单.xlsx", Row.class, pageNo ->
                pageNo <= 2 ? List.of(new Row("第" + pageNo + "页", "部门")) : List.of());

        Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()));
        // 1 行表头 + 2 页各 1 行
        assertThat(wb.getSheetAt(0).getLastRowNum()).isEqualTo(2);
        assertThat(wb.getSheetAt(0).getRow(1).getCell(0).getStringCellValue()).isEqualTo("第1页");
        assertThat(wb.getSheetAt(0).getRow(2).getCell(0).getStringCellValue()).isEqualTo("第2页");
    }

    @Test
    void resets_response_and_raises_write_failed_when_fetcher_blows_up() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> ExcelResponses.writeStream(response, "订单.xlsx", Row.class, pageNo -> {
            throw new IllegalStateException("查库炸了");
        }))
                .isInstanceOf(ExcelException.class)
                .hasFieldOrPropertyWithValue("errorCode", ExcelErrorCode.WRITE_FAILED)
                .hasRootCauseMessage("查库炸了");

        // reset 后 content-type 与 body 都应清空，让 GlobalExceptionHandler 写 JSON
        assertThat(response.getContentAsByteArray()).isEmpty();
    }

    @Test
    void template_has_header_only_and_carries_the_dropdown() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        ExcelResponses.writeTemplate(response, "导入模板.xlsx", Row.class,
                ExcelTemplateOptions.builder()
                        .dropdown("deptName", List.of("研发部", "市场部"))
                        .rows(10)
                        .build());

        Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()));
        assertThat(wb.getSheetAt(0).getLastRowNum()).as("模板只有表头，无数据行").isZero();
        assertThat(wb.getSheetAt(0).getDataValidations()).hasSize(1);
    }

    @Test
    void template_without_explicit_rows_falls_back_to_a_valid_range() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        // rows 未设置时不能把 ROWS_UNSET(-1) 直接当行数用，否则产出的有效性区域非法
        ExcelResponses.writeTemplate(response, "导入模板.xlsx", Row.class,
                ExcelTemplateOptions.builder().dropdown("deptName", List.of("研发部")).build());

        Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()));
        var regions = wb.getSheetAt(0).getDataValidations().get(0).getRegions().getCellRangeAddresses();
        assertThat(regions[0].getFirstRow()).isEqualTo(1);
        assertThat(regions[0].getLastRow()).isEqualTo(ExcelTemplateOptions.DEFAULT_ROWS);
    }
}
