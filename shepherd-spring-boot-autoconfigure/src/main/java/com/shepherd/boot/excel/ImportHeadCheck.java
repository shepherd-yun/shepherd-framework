package com.shepherd.boot.excel;

import cn.idev.excel.context.AnalysisContext;
import cn.idev.excel.enums.HeadKindEnum;
import cn.idev.excel.metadata.Head;
import cn.idev.excel.metadata.data.ReadCellData;
import cn.idev.excel.read.metadata.property.ExcelReadHeadProperty;
import cn.idev.excel.util.ConverterUtils;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 导入文件的表头与 DTO 声明是否一致。
 *
 * <p>为什么非做不可：FastExcel 对只写了表头名的列（{@code @ExcelProperty("名称")}）是按表头文字找列的，
 * 找不到就把这个字段<b>静默丢掉</b>，整列读成 null，不报任何错。用户拿旧模板或拿错模板导入，
 * 看到的是「导入成功」加上一整列空值。
 *
 * <p>判定规则与 FastExcel 的列映射方式对齐，只拒绝真正会读错的情况：
 * <ul>
 *   <li>只写了名字的列：按名字找，<b>位置不限</b>（列顺序调换 FastExcel 能正确映射，没理由拒绝），缺了就拒绝。</li>
 *   <li>同时写了 index 和名字的列：该位置上的表头必须是这个名字。</li>
 *   <li>只写了 index、没写名字的列：没有可比对的依据，不校验。</li>
 *   <li>多出来的非空表头列：只在所有列都写了名字时才拒绝。有列只写 index 时，DTO 本来就只描述了文件的一部分。</li>
 * </ul>
 *
 * <p>必须在 {@code invokeHead} 阶段做：FastExcel 在回调之后才执行按名找列，
 * 并用匹配结果覆盖 head 映射，届时缺失的列已经不在映射里了。
 */
final class ImportHeadCheck {

    private ImportHeadCheck() {
    }

    /** 只在最后一个表头行上校验：多行表头时列名以最后一行为准，与 FastExcel 按名找列的取法一致。 */
    static void check(Map<Integer, ReadCellData<?>> headRow, AnalysisContext context) {
        int rowIndex = context.readRowHolder().getRowIndex();
        if (rowIndex + 1 != context.readSheetHolder().getHeadRowNumber()) {
            return;
        }
        ExcelReadHeadProperty headProperty = context.currentReadHolder().excelReadHeadProperty();
        if (headProperty == null || headProperty.getHeadKind() != HeadKindEnum.CLASS) {
            return;
        }
        boolean trim = Boolean.TRUE.equals(context.currentReadHolder().globalConfiguration().getAutoTrim());
        Map<Integer, String> actual = ConverterUtils.convertToStringMap(headRow, context);

        List<String> problems = compare(headProperty.getHeadMap().values(), actual, trim);
        if (!problems.isEmpty()) {
            throw new ExcelException(ExcelErrorCode.HEAD_MISMATCH,
                    "表头与模板不一致（" + String.join("；", problems) + "），请下载最新模板");
        }
    }

    private static List<String> compare(Iterable<Head> heads, Map<Integer, String> actual, boolean trim) {
        Set<String> actualNames = new LinkedHashSet<>();
        actual.forEach((col, raw) -> {
            String name = normalize(raw, trim);
            if (!name.isEmpty()) {
                actualNames.add(name);
            }
        });

        List<String> problems = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        Set<String> expectedNames = new HashSet<>();
        boolean allNamed = true;

        for (Head head : heads) {
            boolean named = Boolean.TRUE.equals(head.getForceName());
            boolean indexed = Boolean.TRUE.equals(head.getForceIndex());
            if (!named) {
                allNamed = false;
                continue;
            }
            List<String> names = head.getHeadNameList();
            String expected = names.get(names.size() - 1);
            expectedNames.add(expected);
            if (indexed) {
                String at = normalize(actual.get(head.getColumnIndex()), trim);
                if (!expected.equals(at)) {
                    problems.add("第 " + (head.getColumnIndex() + 1) + " 列应为「" + expected + "」，实为「" + at + "」");
                }
            } else if (!actualNames.contains(expected)) {
                missing.add(expected);
            }
        }

        if (!missing.isEmpty()) {
            problems.add("缺少列：" + String.join("、", missing));
        }
        if (allNamed) {
            List<String> extra = actualNames.stream().filter(n -> !expectedNames.contains(n)).toList();
            if (!extra.isEmpty()) {
                problems.add("多出列：" + String.join("、", extra));
            }
        }
        return problems;
    }

    private static String normalize(@Nullable String raw, boolean trim) {
        if (raw == null) {
            return "";
        }
        return trim ? raw.trim() : raw;
    }
}
