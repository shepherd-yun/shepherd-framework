package com.shepherdyun.boot;

import com.shepherdyun.boot.excel.ExcelErrorCode;
import com.shepherdyun.boot.storage.StorageErrorCode;
import com.shepherdyun.boot.web.code.CommonResultCode;
import com.shepherdyun.boot.web.code.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link CommonResultCode}（shepherd-common）与 {@link StorageErrorCode} / {@link ExcelErrorCode}
 * （均在 shepherd-spring-boot-autoconfigure）分散在两个模块，模块边界上 shepherd-common 看不到另两个
 * 枚举，无法在编译期防撞码；各自的值域守卫单测（{@code CommonResultCodeTest} /
 * {@code StorageErrorCodeTest} / {@code ExcelErrorCodeTest}）也只检查自身。本测试运行在唯一同时依赖
 * 三者的模块（autoconfigure），是三个枚举全局唯一性的最后一道守卫。
 *
 * <p>{@code ExcelErrorCodeTest} 里已有的硬编码撞码检查（对着一份手抄的 int[] 断言）是既有覆盖，
 * 本测试是补充，不替代它。
 */
class AllErrorCodesTest {

    private record NamedCode(String enumName, String constantName, int code) {
    }

    @Test
    void codes_are_globally_unique_across_all_three_enums() {
        List<NamedCode> all = new ArrayList<>();
        collect(all, "CommonResultCode", CommonResultCode.values());
        collect(all, "StorageErrorCode", StorageErrorCode.values());
        collect(all, "ExcelErrorCode", ExcelErrorCode.values());

        Map<Integer, NamedCode> firstSeenByCode = new HashMap<>();
        for (NamedCode current : all) {
            NamedCode firstSeen = firstSeenByCode.putIfAbsent(current.code(), current);
            assertThat(firstSeen)
                    .as("%s.%s(%d) 与 %s.%s 撞码", current.enumName(), current.constantName(), current.code(),
                            firstSeen == null ? "?" : firstSeen.enumName(),
                            firstSeen == null ? "?" : firstSeen.constantName())
                    .isNull();
        }
    }

    private static void collect(List<NamedCode> target, String enumName, ErrorCode[] values) {
        for (ErrorCode value : values) {
            target.add(new NamedCode(enumName, ((Enum<?>) value).name(), value.getCode()));
        }
    }
}
