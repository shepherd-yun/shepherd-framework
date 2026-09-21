package com.shepherd.boot.web.filter;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 日志脱敏。
 *
 * <p>刻意只做文本级的按 key 正则替换，不解析成 JSON 树再序列化回去：
 * 这里的产物是排障辅助日志，不追求结构完整性，而请求体本就可能是非法 JSON
 * （坏 JSON 的排障场景恰恰最需要看到原文）。
 */
public class SensitiveMasker {

    private static final String MASK = "\"***\"";
    private static final String TRUNCATED_SUFFIX = "...(truncated)";

    private final List<Pattern> patterns;
    private final int maxLength;

    public SensitiveMasker(List<String> sensitiveKeys, int maxLength) {
        this.maxLength = maxLength;
        this.patterns = sensitiveKeys.stream()
                .map(key -> Pattern.compile(
                        "(\"" + Pattern.quote(key) + "\"\\s*:\\s*)\"[^\"]*\"",
                        Pattern.CASE_INSENSITIVE))
                .toList();
    }

    public String mask(@Nullable String body) {
        if (body == null || body.isEmpty()) {
            return "";
        }
        String masked = body;
        for (Pattern pattern : patterns) {
            // "$1" 必须保持为分组引用，不能过 Matcher.quoteReplacement——那会把它转义成
            // 字面量，导致连键名一起被替换掉。MASK 本身不含 $ 或反斜杠，无需转义。
            masked = pattern.matcher(masked).replaceAll("$1" + MASK);
        }
        return masked.length() > maxLength
                ? masked.substring(0, maxLength) + TRUNCATED_SUFFIX
                : masked;
    }
}
