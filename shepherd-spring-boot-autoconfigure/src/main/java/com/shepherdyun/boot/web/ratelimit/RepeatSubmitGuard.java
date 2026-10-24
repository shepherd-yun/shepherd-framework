package com.shepherdyun.boot.web.ratelimit;

import java.time.Duration;

/** 抽成接口是为了让 Aspect 可以脱离 Redis 单测，并留出替换存储的口子。 */
@FunctionalInterface
public interface RepeatSubmitGuard {

    /** 抢到令牌返回 true；key 已存在（即窗口内重复提交）返回 false。 */
    boolean tryAcquire(String key, Duration ttl);
}
