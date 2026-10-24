package com.shepherdyun.boot.web.ratelimit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

public class RedisRateLimiter implements RateLimiter {

    /**
     * INCR 与 PEXPIRE 必须在同一脚本内。分两次调用时，若在 INCR 之后、PEXPIRE 之前
     * 进程崩溃或连接断开，这个 key 就再也不会过期，该接口被永久限死——
     * 这是限流实现里最典型也最难排查的一个坑。
     */
    private static final RedisScript<Long> SLIDING_WINDOW = new DefaultRedisScript<>("""
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return current
            """, Long.class);

    private final StringRedisTemplate redis;

    public RedisRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public long increment(String key, Duration window) {
        Long current = redis.execute(SLIDING_WINDOW, List.of(key), String.valueOf(window.toMillis()));
        return current == null ? 1L : current;
    }
}
