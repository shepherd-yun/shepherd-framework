package com.shepherd.boot.serialno;

import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 日期段格式化与计数器 key 的 TTL 推导。
 *
 * <p><b>唯一的安全不变式：TTL 只能偏长，不能偏短。</b> 偏长只是 key 多留一阵——下个日期段
 * 本就换用新 key，无害；偏短会让当前 key 中途过期、序号回到 1，直接重号。
 *
 * <p>推导方式（边界法）：按顺序检查下一分钟 / 下一小时 / 下一天 / 下一月 / 下一年这几个
 * 截断边界，取第一个使格式化结果发生变化的边界 B，{@code TTL = (B - now) + 1 天}。
 * 格式变化点必然落在 {@code (now, B]} 内，故剩余寿命 {@code R <= B - now < TTL}，不变式成立。
 * 加 1 天缓冲是为了容忍时钟漂移与跨边界瞬间的并发调用。
 *
 * <p>已知的宽松之处：边界集合从「分钟」起，所以秒级 pattern 拿到的 TTL 会远长于日期段寿命，
 * 极端情况下（恰好在整分钟发号）推不出边界而落到「不设 TTL」。按上面的不变式这都是安全的，
 * 且单号的日期段不会用到秒级，不为此增加复杂度。
 */
final class DateSegments {

    private static final Map<String, DateTimeFormatter> FORMATTERS = new ConcurrentHashMap<>();
    private static final Duration MARGIN = Duration.ofDays(1);

    private DateSegments() {
    }

    /** datePattern 为 null 时返回空串（= 无日期段）。 */
    static String segment(@Nullable String datePattern, LocalDateTime now) {
        return datePattern == null ? "" : formatter(datePattern).format(now);
    }

    /** 计数器 key 的 TTL；返回 null 表示不设过期。 */
    static @Nullable Duration ttl(@Nullable String datePattern, LocalDateTime now) {
        if (datePattern == null) {
            return null;
        }
        DateTimeFormatter formatter = formatter(datePattern);
        String current = formatter.format(now);
        for (LocalDateTime boundary : boundaries(now)) {
            if (!formatter.format(boundary).equals(current)) {
                return Duration.between(now, boundary).plus(MARGIN);
            }
        }
        return null;
    }

    private static List<LocalDateTime> boundaries(LocalDateTime now) {
        LocalDateTime midnight = now.truncatedTo(ChronoUnit.DAYS);
        return List.of(
                now.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1),
                now.truncatedTo(ChronoUnit.HOURS).plusHours(1),
                midnight.plusDays(1),
                midnight.withDayOfMonth(1).plusMonths(1),
                midnight.withDayOfMonth(1).withMonth(1).plusYears(1));
    }

    private static DateTimeFormatter formatter(String pattern) {
        return FORMATTERS.computeIfAbsent(pattern, DateTimeFormatter::ofPattern);
    }
}
