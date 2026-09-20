package com.shepherd.boot.web.ratelimit;

import java.time.Duration;

@FunctionalInterface
public interface RateLimiter {

    /**
     * 在窗口内自增并返回自增后的计数。首次自增时必须同时设置过期时间，
     * 且两个操作必须原子——否则自增成功、设过期前崩溃会留下永不过期的 key，
     * 把该接口永久锁死。
     */
    long increment(String key, Duration window);
}
