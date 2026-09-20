package com.shepherd.boot.serialno;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedisSerialNoGeneratorTest {

    /**
     * 内存假 Redis：按 key 累加，同时记录每次脚本调用的 key 与实参，
     * 供断言 key 形状、INCRBY 的 count、TTL 毫秒数是否正确换算。
     */
    static class FakeRedis extends StringRedisTemplate {
        final Map<String, Long> counters = new HashMap<>();
        final List<String> calledKeys = new ArrayList<>();
        final List<Object[]> calledArgs = new ArrayList<>();

        @Override
        @SuppressWarnings("unchecked")
        public <T> T execute(RedisScript<T> script, List<String> keys, Object... args) {
            String key = keys.get(0);
            calledKeys.add(key);
            calledArgs.add(args);
            long count = Long.parseLong(String.valueOf(args[0]));
            return (T) counters.merge(key, count, Long::sum);
        }

        String lastTtlArg() {
            Object[] args = calledArgs.get(calledArgs.size() - 1);
            return String.valueOf(args[1]);
        }

        String lastCountArg() {
            Object[] args = calledArgs.get(calledArgs.size() - 1);
            return String.valueOf(args[0]);
        }
    }

    private static final SerialNoRule DAILY = SerialNoRule.of("BL", "yyyyMMdd", 4);

    private FakeRedis redis;
    private RedisSerialNoGenerator generator;
    private ListAppender<ILoggingEvent> appender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        redis = new FakeRedis();
        generator = new RedisSerialNoGenerator(redis, "sn:");
        logger = (Logger) LoggerFactory.getLogger(RedisSerialNoGenerator.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
    }

    private static String today() {
        return DateTimeFormatter.ofPattern("yyyyMMdd").format(LocalDateTime.now());
    }

    @Test
    void first_serial_no_starts_at_one_and_is_zero_padded() {
        assertThat(generator.next(DAILY)).isEqualTo("BL" + today() + "0001");
        assertThat(generator.next(DAILY)).isEqualTo("BL" + today() + "0002");
    }

    @Test
    void key_is_prefix_plus_date_segment() {
        generator.next(DAILY);

        assertThat(redis.calledKeys).containsExactly("sn:BL:" + today());
        assertThat(redis.lastCountArg()).isEqualTo("1");
    }

    @Test
    void key_has_no_date_part_when_rule_has_no_date_pattern() {
        generator.next(SerialNoRule.of("BL", 4));

        assertThat(redis.calledKeys).containsExactly("sn:BL");
    }

    @Test
    void ttl_arg_is_zero_when_rule_has_no_date_pattern() {
        generator.next(SerialNoRule.of("BL", 4));

        assertThat(redis.lastTtlArg()).isEqualTo("0");
    }

    @Test
    void ttl_arg_is_positive_millis_when_rule_has_date_pattern() {
        generator.next(DAILY);

        assertThat(Long.parseLong(redis.lastTtlArg())).isPositive();
    }

    @Test
    void batch_returns_consecutive_serial_nos_in_one_round_trip() {
        List<String> nos = generator.nextBatch(DAILY, 3);

        assertThat(nos).containsExactly(
                "BL" + today() + "0001",
                "BL" + today() + "0002",
                "BL" + today() + "0003");
        assertThat(redis.calledKeys).hasSize(1);
        assertThat(redis.lastCountArg()).isEqualTo("3");
    }

    @Test
    void count_below_one_is_rejected() {
        assertThatThrownBy(() -> generator.nextBatch(DAILY, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("count");
    }

    /** 位数用尽时自动加宽，不截断、不报错。 */
    @Test
    void seq_widens_instead_of_truncating_when_exhausted() {
        redis.counters.put("sn:BL:" + today(), 9999L);

        assertThat(generator.next(DAILY)).isEqualTo("BL" + today() + "10000");
    }

    /** 溢出 WARN 只在跨过 10^seqLength 的那一次打，否则之后每次调用都会刷日志。 */
    @Test
    void overflow_warns_exactly_once() {
        redis.counters.put("sn:BL:" + today(), 9998L);

        generator.next(DAILY);   // 9999
        generator.next(DAILY);   // 10000 ← 跨越，打 WARN
        generator.next(DAILY);   // 10001

        List<ILoggingEvent> warns = appender.list.stream()
                .filter(e -> e.getLevel() == Level.WARN)
                .toList();
        assertThat(warns).hasSize(1);
        assertThat(warns.get(0).getFormattedMessage()).contains("BL").contains("seqLength=4");
    }

    /** 批量调用一次跨过阈值时也要打，且只打一次。 */
    @Test
    void overflow_warns_once_for_a_batch_that_crosses_the_threshold() {
        redis.counters.put("sn:BL:" + today(), 9998L);

        generator.nextBatch(DAILY, 5);   // 9999 ~ 10003

        assertThat(appender.list.stream().filter(e -> e.getLevel() == Level.WARN).toList())
                .hasSize(1);
    }
}
