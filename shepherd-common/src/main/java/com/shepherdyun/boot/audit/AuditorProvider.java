package com.shepherdyun.boot.audit;

import java.util.Optional;

/**
 * 提供当前操作者 id，供 MyBatis 审计字段自动填充使用。
 * 抽成接口是为了让 mybatis 部分不依赖 security：
 * 有 Spring Security 时由 SecurityContextAuditorProvider 实现，否则退化为空。
 */
@FunctionalInterface
public interface AuditorProvider {
    Optional<Long> currentUserId();
}
