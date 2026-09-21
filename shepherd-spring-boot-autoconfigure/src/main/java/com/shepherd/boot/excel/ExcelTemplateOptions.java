package com.shepherd.boot.excel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 导入模板的选项。下拉按 DTO 字段名指定——列索引写法在 DTO 中间插一列时会静默错位。
 */
public final class ExcelTemplateOptions {

    /** rows 未显式设置时的哨兵值，由调用方回落到 shepherd.excel.template.default-rows。 */
    public static final int ROWS_UNSET = -1;

    /** 调用方也没给出回落行数时使用的行数，与 shepherd.excel.template.default-rows 的默认值一致。 */
    public static final int DEFAULT_ROWS = 500;

    private final Map<String, List<String>> dropdowns;
    private final int rows;

    private ExcelTemplateOptions(Map<String, List<String>> dropdowns, int rows) {
        this.dropdowns = dropdowns;
        this.rows = rows;
    }

    public static ExcelTemplateOptions none() {
        return new ExcelTemplateOptions(Map.of(), ROWS_UNSET);
    }

    public static Builder builder() {
        return new Builder();
    }

    public Map<String, List<String>> dropdowns() {
        return dropdowns;
    }

    public int rows() {
        return rows;
    }

    public static final class Builder {
        private final Map<String, List<String>> dropdowns = new LinkedHashMap<>();
        private int rows = ROWS_UNSET;

        public Builder dropdown(String fieldName, List<String> options) {
            this.dropdowns.put(fieldName, List.copyOf(new ArrayList<>(options)));
            return this;
        }

        public Builder rows(int rows) {
            this.rows = rows;
            return this;
        }

        public ExcelTemplateOptions build() {
            return new ExcelTemplateOptions(new LinkedHashMap<>(dropdowns), rows);
        }
    }
}
