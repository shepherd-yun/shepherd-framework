package com.shepherd.boot.serialno;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SerialNoRuleTest {

    @Test
    void builder_builds_rule() {
        SerialNoRule rule = SerialNoRule.builder()
                .prefix("BL")
                .datePattern("yyyyMMdd")
                .seqLength(4)
                .build();

        assertThat(rule.prefix()).isEqualTo("BL");
        assertThat(rule.datePattern()).isEqualTo("yyyyMMdd");
        assertThat(rule.seqLength()).isEqualTo(4);
    }

    @Test
    void of_without_date_pattern_means_no_date_segment() {
        SerialNoRule rule = SerialNoRule.of("BL", 4);

        assertThat(rule.datePattern()).isNull();
        assertThat(rule.prefix()).isEqualTo("BL");
        assertThat(rule.seqLength()).isEqualTo(4);
    }

    @Test
    void blank_prefix_is_rejected() {
        assertThatThrownBy(() -> SerialNoRule.of("  ", "yyyyMMdd", 4))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("prefix");
    }

    @Test
    void seq_length_out_of_range_is_rejected() {
        assertThatThrownBy(() -> SerialNoRule.of("BL", "yyyyMMdd", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("seqLength");
        assertThatThrownBy(() -> SerialNoRule.of("BL", "yyyyMMdd", 19))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("seqLength");
    }

    /** '#' 是 DateTimeFormatter 的保留字符，ofPattern 阶段就会炸。 */
    @Test
    void unparsable_date_pattern_is_rejected() {
        assertThatThrownBy(() -> SerialNoRule.of("BL", "yyyy#MM", 4))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("datePattern");
    }

    /** VV（时区 ID）能被 ofPattern 解析，但格式化 LocalDateTime 时才抛——必须在构造期就拦住。 */
    @Test
    void date_pattern_unsupported_at_format_time_is_rejected() {
        assertThatThrownBy(() -> SerialNoRule.of("BL", "VV", 4))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("datePattern");
    }

    @Test
    void blank_date_pattern_is_rejected() {
        assertThatThrownBy(() -> SerialNoRule.of("BL", "   ", 4))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("datePattern");
    }
}
