package com.shepherdyun.boot.excel;

import cn.idev.excel.annotation.ExcelProperty;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把 DTO 字段名解析成 0-based 列索引。
 *
 * <p>模板下拉按字段名而非列索引定位：在 DTO 中间插一列时，列索引写法会静默错位，
 * 字段名写法则在生成模板时立即失败。
 *
 * <p>排序规则与 FastExcel 的表头一致：显式设了 {@code index()} 的字段占据该列，
 * 未设的按声明顺序填入剩余列位。
 */
final class HeadColumns {

    private HeadColumns() {
    }

    static int indexOf(Class<?> head, String fieldName) {
        Integer index = resolveAll(head).get(fieldName);
        if (index == null) {
            throw new ExcelException(ExcelErrorCode.UNKNOWN_TEMPLATE_FIELD,
                    "模板下拉指定的字段名 '" + fieldName + "' 在 " + head.getSimpleName()
                            + " 中不存在，或该字段没有标注 @ExcelProperty");
        }
        return index;
    }

    private static Map<String, Integer> resolveAll(Class<?> head) {
        List<Field> explicit = new ArrayList<>();
        List<Field> implicit = new ArrayList<>();
        for (Field field : head.getDeclaredFields()) {
            ExcelProperty ann = field.getAnnotation(ExcelProperty.class);
            if (ann == null) {
                continue;
            }
            if (ann.index() >= 0) {
                explicit.add(field);
            } else {
                implicit.add(field);
            }
        }

        Map<String, Integer> result = new LinkedHashMap<>();
        boolean[] taken = new boolean[explicit.size() + implicit.size()
                + explicit.stream().mapToInt(f -> f.getAnnotation(ExcelProperty.class).index() + 1).max().orElse(0)];
        for (Field field : explicit) {
            int index = field.getAnnotation(ExcelProperty.class).index();
            result.put(field.getName(), index);
            taken[index] = true;
        }

        int cursor = 0;
        for (Field field : implicit) {
            while (taken[cursor]) {
                cursor++;
            }
            result.put(field.getName(), cursor);
            taken[cursor] = true;
        }
        return result;
    }
}
