package com.shepherdyun.boot.security;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

public class RedisLoginTokenStore implements LoginTokenStore {

    private final StringRedisTemplate redis;

    public RedisLoginTokenStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    private String key(Long userId, String jti) {
        return "login:token:" + userId + ":" + jti;
    }

    @Override
    public void save(Long userId, String jti, long ttlSeconds) {
        redis.opsForValue().set(key(userId, jti), "1", Duration.ofSeconds(ttlSeconds));
    }

    @Override
    public boolean isValid(Long userId, String jti) {
        return Boolean.TRUE.equals(redis.hasKey(key(userId, jti)));
    }

    @Override
    public void revoke(Long userId, String jti) {
        redis.delete(key(userId, jti));
    }
}
