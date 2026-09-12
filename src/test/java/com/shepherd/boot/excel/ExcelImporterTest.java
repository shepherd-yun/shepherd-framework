package com.shepherd.boot.excel;

import cn.idev.excel.FastExcel;
import cn.idev.excel.annotation.ExcelProperty;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExcelImporterTest {

    public static class UserDto {
        @ExcelProperty("姓名")
        @NotBlank(message = "姓名不能为空")
        private String name;

        @ExcelProperty("部门")
        private String deptName;

        public UserDto() {
        }

        public UserDto(String name, String deptName) {
            this.name = name;
            this.deptName = deptName;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDeptName() { return deptName; }
        public void setDeptName(String deptName) { this.deptName = deptName; }
    }

    private static MockMultipartFile xlsx(String fileName, List<UserDto> rows) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        FastExcel.write(out, UserDto.class).sheet("Sheet1").doWrite(rows);
        return new MockMultipartFile("file", fileName,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                out.toByteArray());
    }

    private ExcelImporter importer(Validator validator) {
        ShepherdExcelProperties props = new ShepherdExcelProperties();
        props.setBatchSize(2);
        return new ExcelImporter(props, validator);
    }

    @Test
    void reads_rows_in_batches_and_reports_success() {
        List<List<UserDto>> batches = new ArrayList<>();
        var file = xlsx("users.xlsx", List.of(
                new UserDto("张三", "研发部"),
                new UserDto("李四", "市场部"),
                new UserDto("王五", "运维部")));

        ExcelImportResult<UserDto> r =
                importer(null).read(file, UserDto.class, b -> batches.add(List.copyOf(b)));

        assertThat(r.total()).isEqualTo(3);
        assertThat(r.succeeded()).isEqualTo(3);
        assertThat(r.failed()).isZero();
        assertThat(batches).as("batchSize=2 → 2 批").hasSize(2);
        assertThat(batches.get(0)).extracting(UserDto::getName).containsExactly("张三", "李四");
    }

    @Test
    void rows_failing_validation_are_reported_and_never_reach_the_consumer() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            List<UserDto> consumed = new ArrayList<>();
            var file = xlsx("users.xlsx", List.of(
                    new UserDto("张三", "研发部"),
                    new UserDto("", "市场部")));

            ExcelImportResult<UserDto> r = importer(factory.getValidator())
                    .read(file, UserDto.class, consumed::addAll);

            assertThat(r.total()).isEqualTo(2);
            assertThat(r.succeeded()).isEqualTo(1);
            assertThat(r.failed()).isEqualTo(1);
            assertThat(r.errors()).singleElement().satisfies(e -> {
                assertThat(e.rowNum()).as("表头第 1 行，第 2 条数据是第 3 行").isEqualTo(3);
                assertThat(e.column()).isEqualTo("name");
                assertThat(e.message()).isEqualTo("姓名不能为空");
            });
            assertThat(consumed).extracting(UserDto::getName).containsExactly("张三");
        }
    }

    @Test
    void rejects_unsupported_extension_before_parsing() {
        var file = new MockMultipartFile("file", "users.pdf", "application/pdf", new byte[]{1, 2, 3});

        assertThatThrownBy(() -> importer(null).read(file, UserDto.class, b -> { }))
                .isInstanceOf(ExcelException.class)
                .hasFieldOrPropertyWithValue("errorCode", ExcelErrorCode.UNSUPPORTED_FORMAT);
    }

    @Test
    void rejects_a_file_with_no_data_rows() {
        var file = xlsx("users.xlsx", List.of());

        assertThatThrownBy(() -> importer(null).read(file, UserDto.class, b -> { }))
                .isInstanceOf(ExcelException.class)
                .hasFieldOrPropertyWithValue("errorCode", ExcelErrorCode.EMPTY_FILE);
    }

    @Test
    void wraps_corrupt_file_into_parse_failed_keeping_the_cause() throws Exception {
        // 注意：随便一串非 Excel 字节不会触发解析失败——FastExcel 按魔数嗅探格式，
        // 认不出 xls/xlsx 时回落成 CSV 解析，结果是「0 行」而非异常。
        // 要测 PARSE_FAILED 必须给出「自称 OOXML 但结构损坏」的字节：截断一个真实 xlsx。
        byte[] intact = xlsx("users.xlsx", List.of(new UserDto("张三", "研发部"))).getBytes();
        byte[] truncated = java.util.Arrays.copyOf(intact, intact.length / 2);
        var file = new MockMultipartFile("file", "users.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                truncated);

        assertThatThrownBy(() -> importer(null).read(file, UserDto.class, b -> { }))
                .isInstanceOf(ExcelException.class)
                .hasFieldOrPropertyWithValue("errorCode", ExcelErrorCode.PARSE_FAILED)
                .hasCauseInstanceOf(Exception.class);
    }
}
