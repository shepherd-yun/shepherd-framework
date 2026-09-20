package com.shepherd.boot.autoconfigure;

import com.shepherd.boot.web.ratelimit.*;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code @RepeatSubmit} / {@code @RateLimit} 的装配。
 *
 * <p>无 AOP 或无 Redis 时静默不装配——这是可选增强能力，不像 LoginTokenStore 那样
 * 关系到安全语义，没有 fail-fast 的理由。用了注解却没装配的后果是注解无效，
 * 因此 README 需注明两项前提。
 */
@AutoConfiguration(afterName = "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({Aspect.class, StringRedisTemplate.class})
@ConditionalOnBean(StringRedisTemplate.class)
public class ShepherdRateLimitAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(RepeatSubmitGuard.class)
    public RepeatSubmitGuard redisRepeatSubmitGuard(StringRedisTemplate redis) {
        return new RedisRepeatSubmitGuard(redis);
    }

    @Bean
    @ConditionalOnMissingBean
    public RepeatSubmitAspect repeatSubmitAspect(RepeatSubmitGuard guard, JsonMapper jsonMapper) {
        return new RepeatSubmitAspect(guard, jsonMapper);
    }

    @Bean
    @ConditionalOnMissingBean(RateLimiter.class)
    public RateLimiter redisRateLimiter(StringRedisTemplate redis) {
        return new RedisRateLimiter(redis);
    }

    @Bean
    @ConditionalOnMissingBean
    public RateLimitAspect rateLimitAspect(RateLimiter limiter) {
        return new RateLimitAspect(limiter);
    }
}
