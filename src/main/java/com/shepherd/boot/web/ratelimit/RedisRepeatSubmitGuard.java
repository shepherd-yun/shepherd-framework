package com.shepherd.boot.web.ratelimit;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

public class RedisRepeatSubmitGuard implements RepeatSubmitGuard {

    private final StringRedisTemplate redis;

    public RedisRepeatSubmitGuard(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean tryAcquire(String key, Duration ttl) {
        // SET NX EX 是单条原子命令，不存在「设值成功但未设过期」的中间态
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, "1", ttl));
    }
}
