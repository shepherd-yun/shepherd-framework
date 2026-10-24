package com.shepherdyun.boot.excel;

import cn.idev.excel.write.handler.SheetWriteHandler;
import cn.idev.excel.write.handler.context.SheetWriteHandlerContext;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.SheetVisibility;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddressList;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 给模板的指定列挂下拉（数据有效性）。
 *
 * <p>POI 有两种列表约束，各有一个会导致文件静默损坏的坑：
 * <ul>
 *   <li><b>内联</b>（{@code createExplicitListConstraint}）：选项逗号分隔内联进 XML。
 *       Excel 限制该串总长 ≤255 字符，超出后文件能下载但 Excel 打开报「已损坏」；
 *       且选项自身含逗号时会被拆成多项。</li>
 *   <li><b>公式</b>（{@code createFormulaListConstraint}）：引用区域，无长度限制、不受逗号影响。</li>
 * </ul>
 * 本类按规则自动选择，调用方无感。
 */
class DropdownSheetWriteHandler implements SheetWriteHandler {

    /** Excel 对内联列表约束字符串的总长限制。 */
    private static final int INLINE_LIMIT = 255;

    private final Class<?> head;
    private final Map<String, List<String>> dropdowns;
    private final int rows;
    private final String hiddenSheetName;

    DropdownSheetWriteHandler(Class<?> head, Map<String, List<String>> dropdowns,
                              int rows, String hiddenSheetName) {
        this.head = head;
        this.dropdowns = new LinkedHashMap<>(dropdowns);
        this.rows = rows;
        this.hiddenSheetName = hiddenSheetName;
    }

    @Override
    public void afterSheetCreate(SheetWriteHandlerContext context) {
        if (dropdowns.isEmpty()) {
            return;
        }
        Sheet sheet = context.getWriteSheetHolder().getSheet();
        DataValidationHelper helper = sheet.getDataValidationHelper();
        int hiddenColumn = 0;

        for (Map.Entry<String, List<String>> entry : dropdowns.entrySet()) {
            List<String> options = entry.getValue();
            if (options.isEmpty()) {
                continue;
            }
            int column = HeadColumns.indexOf(head, entry.getKey());
            // 第 0 行是表头，数据从第 1 行起
            CellRangeAddressList range = new CellRangeAddressList(1, rows, column, column);

            DataValidationConstraint constraint;
            if (fitsInline(options)) {
                constraint = helper.createExplicitListConstraint(options.toArray(String[]::new));
            } else {
                writeHiddenColumn(sheet.getWorkbook(), hiddenColumn, options);
                String formula = "%s!$%s$1:$%s$%d".formatted(
                        hiddenSheetName, columnLetter(hiddenColumn), columnLetter(hiddenColumn), options.size());
                constraint = helper.createFormulaListConstraint(formula);
                hiddenColumn++;
            }

            DataValidation validation = helper.createValidation(constraint, range);
            validation.setShowErrorBox(true);
            sheet.addValidationData(validation);
        }
    }

    private boolean fitsInline(List<String> options) {
        if (options.stream().anyMatch(o -> o.contains(","))) {
            return false;
        }
        return String.join(",", options).length() <= INLINE_LIMIT;
    }

    private void writeHiddenColumn(Workbook workbook, int column, List<String> options) {
        Sheet hidden = workbook.getSheet(hiddenSheetName);
        if (hidden == null) {
            hidden = workbook.createSheet(hiddenSheetName);
            workbook.setSheetVisibility(workbook.getSheetIndex(hidden), SheetVisibility.HIDDEN);
        }
        for (int i = 0; i < options.size(); i++) {
            Row row = hidden.getRow(i);
            if (row == null) {
                row = hidden.createRow(i);
            }
            row.createCell(column).setCellValue(options.get(i));
        }
    }

    /** 隐藏列数量远小于 26，单字母足够；留此方法是为了让公式拼接处语义清楚。 */
    private static String columnLetter(int column) {
        return String.valueOf((char) ('A' + column));
    }
}
