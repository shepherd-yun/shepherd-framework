package com.shepherd.boot.serialno;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class DateSegmentsTest {

    @Test
    void null_pattern_has_no_segment_and_no_ttl() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 11, 10, 30);

        assertThat(DateSegments.segment(null, now)).isEmpty();
        assertThat(DateSegments.ttl(null, now)).isNull();
    }

    @Test
    void segment_is_the_formatted_pattern() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 11, 10, 30);

        assertThat(DateSegments.segment("yyyyMMdd", now)).isEqualTo("20260911");
        assertThat(DateSegments.segment("yyMM", now)).isEqualTo("2609");
        assertThat(DateSegments.segment("yyyy", now)).isEqualTo("2026");
    }

    /** 日粒度：到次日 00:00 还有 13h30m，加 1 天缓冲。 */
    @Test
    void daily_ttl_reaches_next_day_plus_margin() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 11, 10, 30);

        assertThat(DateSegments.ttl("yyyyMMdd", now))
                .isEqualTo(Duration.ofHours(13).plusMinutes(30).plusDays(1));
    }

    /** 月粒度在月中：到 10-01 00:00 还有 20 天 13h30m，加 1 天缓冲。 */
    @Test
    void monthly_ttl_reaches_next_month_plus_margin() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 11, 10, 30);

        assertThat(DateSegments.ttl("yyMM", now))
                .isEqualTo(Duration.ofDays(19).plusHours(13).plusMinutes(30).plusDays(1));
    }

    /** 月粒度在月末最后一小时：下一小时边界（10-01 00:00）就已让月份段变化，TTL = 1h + 1 天。 */
    @Test
    void monthly_ttl_on_last_day_of_month_crosses_at_the_next_hour() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 30, 23, 0);

        assertThat(DateSegments.ttl("yyMM", now))
                .isEqualTo(Duration.ofHours(1).plusDays(1));
    }

    @Test
    void yearly_ttl_reaches_next_year_plus_margin() {
        LocalDateTime now = LocalDateTime.of(2026, 12, 31, 23, 0);

        assertThat(DateSegments.ttl("yyyy", now))
                .isEqualTo(Duration.ofHours(1).plusDays(1));
    }

    /** 不含任何日期字段的 pattern 推不出边界，落到「不设 TTL」，key 常驻。 */
    @Test
    void pattern_without_date_fields_has_no_ttl() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 11, 10, 30);

        assertThat(DateSegments.ttl("'FIXED'", now)).isNull();
    }

    /**
     * 核心不变式：TTL 必须覆盖「日期段真正剩余的寿命」。
     * 逐分钟向前走 3 天，任意时刻、任意粒度都不许 TTL < 剩余寿命。
     */
    @Test
    void ttl_always_covers_remaining_segment_lifetime() {
        for (String pattern : new String[]{"yyyyMMdd", "yyMM", "yyyy", "yyyyMMddHH"}) {
            LocalDateTime cursor = LocalDateTime.of(2026, 12, 30, 22, 57);
            for (int i = 0; i < 3 * 24 * 60; i++) {
                LocalDateTime now = cursor.plusMinutes(i);
                Duration ttl = DateSegments.ttl(pattern, now);
                if (ttl == null) {
                    continue;
                }
                Duration remaining = remainingLifetime(pattern, now);
                assertThat(ttl)
                        .as("pattern=%s now=%s", pattern, now)
                        .isGreaterThanOrEqualTo(remaining);
            }
        }
    }

    /**
     * 求出日期段真正改变的时刻。二分而不是逐分钟扫——逐分钟扫在这个用例的规模下
     * 会跑出几十亿次 format。上面这些 pattern 的格式化结果随时间单调递增，
     * 所以「是否已变化」这个谓词是单调的（先 false 后 true），二分成立。
     */
    private static Duration remainingLifetime(String pattern, LocalDateTime now) {
        String current = DateSegments.segment(pattern, now);
        long lo = 1;
        long hi = 400L * 24 * 60;
        if (DateSegments.segment(pattern, now.plusMinutes(hi)).equals(current)) {
            throw new IllegalStateException("日期段在 400 天内未变化：" + pattern);
        }
        while (lo < hi) {
            long mid = lo + (hi - lo) / 2;
            if (DateSegments.segment(pattern, now.plusMinutes(mid)).equals(current)) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return Duration.between(now, now.plusMinutes(lo));
    }
}
