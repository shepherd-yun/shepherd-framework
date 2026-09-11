package com.shepherd.boot.serialno;

import org.jspecify.annotations.Nullable;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 单号规则：{@code prefix + 日期段 + 补零序号}，如 {@code BL202609110001}。
 *
 * <p>日期段的格式本身就决定了序号的重置粒度——Redis 计数器 key 就是
 * {@code prefix + 格式化结果}，日期段一变就换了一个 key，序号自然从 1 开始。
 * {@code datePattern} 为 null 表示无日期段，序号永不重置。
 *
 * <p>校验在构造期 fail fast 抛 {@link IllegalArgumentException}：规则写错是程序员错误，
 * 不是业务错误，所以既不抛 {@code BizException} 也不占错误码。
 *
 * <p>建议在 service 里存成 {@code static final} 常量，避免同一单号在多处调用时写出不一致的规则。
 */
public record SerialNoRule(String prefix, @Nullable String datePattern, int seqLength) {

    /** 上界取 long 的十进制位数，再长无意义。 */
    private static final int MAX_SEQ_LENGTH = 18;

    public SerialNoRule {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("单号规则 prefix 不可为空");
        }
        if (seqLength < 1 || seqLength > MAX_SEQ_LENGTH) {
            throw new IllegalArgumentException(
                    "单号规则 seqLength 必须在 1~" + MAX_SEQ_LENGTH + " 之间，实际 " + seqLength);
        }
        if (datePattern != null) {
            if (datePattern.isBlank()) {
                throw new IllegalArgumentException("单号规则 datePattern 不可为空白字符串，无日期段请传 null");
            }
            String sample;
            try {
                // 不止要能解析 pattern，还要能真的格式化 LocalDateTime——
                // 例如 VV（时区 ID）解析得过，格式化 LocalDateTime 时才抛。
                sample = DateTimeFormatter.ofPattern(datePattern).format(LocalDateTime.now());
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("单号规则 datePattern 非法：" + datePattern, e);
            }
            if (sample.isBlank()) {
                throw new IllegalArgumentException("单号规则 datePattern 的格式化结果为空：" + datePattern);
            }
        }
    }

    public static SerialNoRule of(String prefix, @Nullable String datePattern, int seqLength) {
        return new SerialNoRule(prefix, datePattern, seqLength);
    }

    /** 无日期段，序号永不重置。 */
    public static SerialNoRule of(String prefix, int seqLength) {
        return new SerialNoRule(prefix, null, seqLength);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private @Nullable String prefix;
        private @Nullable String datePattern;
        private int seqLength = 4;

        private Builder() {
        }

        public Builder prefix(String prefix) {
            this.prefix = prefix;
            return this;
        }

        /** 传 null 表示无日期段。 */
        public Builder datePattern(@Nullable String datePattern) {
            this.datePattern = datePattern;
            return this;
        }

        public Builder seqLength(int seqLength) {
            this.seqLength = seqLength;
            return this;
        }

        public SerialNoRule build() {
            if (prefix == null) {
                throw new IllegalArgumentException("单号规则 prefix 不可为空");
            }
            return new SerialNoRule(prefix, datePattern, seqLength);
        }
    }
}
