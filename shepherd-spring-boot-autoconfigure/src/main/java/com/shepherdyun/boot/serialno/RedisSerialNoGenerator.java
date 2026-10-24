package com.shepherdyun.boot.serialno;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Redis 计数器实现。key 形如 {@code shepherd:serialno:BL:20260911}，
 * 无日期段时为 {@code shepherd:serialno:BL}。
 */
public class RedisSerialNoGenerator implements SerialNoGenerator {

    private static final Logger log = LoggerFactory.getLogger(RedisSerialNoGenerator.class);

    /**
     * INCRBY 与 PEXPIRE 必须在同一脚本内。分两次调用时，若在 INCRBY 之后、PEXPIRE 之前
     * 进程崩溃或连接断开，这个 key 就再也不会过期——与 RedisRateLimiter 注释里同一个坑。
     *
     * <p>{@code current == ARGV[1]} 即「key 刚被本次调用创建」，只有这一次设 TTL，
     * 避免每次取号都给计数器续期（续期会让日期段过去很久的 key 一直活着）。
     * {@code ARGV[2] == '0'} 表示本规则不设 TTL（无日期段，序号永不重置）。
     */
    private static final RedisScript<Long> NEXT_SEQ = new DefaultRedisScript<>("""
            local current = redis.call('INCRBY', KEYS[1], ARGV[1])
            if current == tonumber(ARGV[1]) and ARGV[2] ~= '0' then
              redis.call('PEXPIRE', KEYS[1], ARGV[2])
            end
            return current
            """, Long.class);

    private final StringRedisTemplate redis;
    private final String keyPrefix;

    public RedisSerialNoGenerator(StringRedisTemplate redis, String keyPrefix) {
        this.redis = redis;
        this.keyPrefix = keyPrefix;
    }

    @Override
    public String next(SerialNoRule rule) {
        return nextBatch(rule, 1).get(0);
    }

    @Override
    public List<String> nextBatch(SerialNoRule rule, int count) {
        if (count < 1) {
            throw new IllegalArgumentException("取号 count 必须 >= 1，实际 " + count);
        }

        LocalDateTime now = LocalDateTime.now();
        String dateSeg = DateSegments.segment(rule.datePattern(), now);
        Duration ttl = DateSegments.ttl(rule.datePattern(), now);
        String key = dateSeg.isEmpty()
                ? keyPrefix + rule.prefix()
                : keyPrefix + rule.prefix() + ":" + dateSeg;

        Long current = redis.execute(NEXT_SEQ, List.of(key),
                String.valueOf(count),
                ttl == null ? "0" : String.valueOf(ttl.toMillis()));
        if (current == null) {
            throw new IllegalStateException("单号自增脚本未返回结果，key=" + key);
        }

        long from = current - count + 1;
        warnOnOverflow(rule, dateSeg, from, current);

        String format = "%0" + rule.seqLength() + "d";
        List<String> serialNos = new ArrayList<>(count);
        for (long seq = from; seq <= current; seq++) {
            serialNos.add(rule.prefix() + dateSeg + String.format(format, seq));
        }
        return serialNos;
    }

    /**
     * %0Nd 在位数不足时天然加宽，不截断、不报错——宁可单号变长也不中断业务。
     * WARN 只在恰好跨过 10^seqLength 的那一次打，否则此后每一次取号都会刷日志。
     */
    private void warnOnOverflow(SerialNoRule rule, String dateSeg, long from, long to) {
        long threshold = (long) Math.pow(10, rule.seqLength());
        if (from <= threshold && to >= threshold) {
            log.warn("serial-no seq overflow: prefix={} date={} seq={} > seqLength={}，"
                            + "单号长度将变长，请调大 seqLength",
                    rule.prefix(), dateSeg, threshold, rule.seqLength());
        }
    }
}
