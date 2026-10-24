package com.shepherdyun.boot.autoconfigure;

import com.shepherdyun.boot.web.ratelimit.RateLimitAspect;
import com.shepherdyun.boot.web.ratelimit.RepeatSubmitAspect;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ShepherdRateLimitAutoConfigurationTest {

    @Configuration(proxyBeanMethods = false)
    static class RedisPresent {
        @Bean
        StringRedisTemplate stringRedisTemplate() {
            return mock(StringRedisTemplate.class);
        }
    }

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ShepherdRateLimitAutoConfiguration.class));

    @Test
    void aspects_are_registered_when_redis_is_present() {
        runner.withUserConfiguration(RedisPresent.class)
              .withBean(tools.jackson.databind.json.JsonMapper.class,
                        () -> tools.jackson.databind.json.JsonMapper.builder().build())
              .run(ctx -> {
                  assertThat(ctx).hasSingleBean(RepeatSubmitAspect.class);
                  assertThat(ctx).hasSingleBean(RateLimitAspect.class);
              });
    }

    /** 没有 Redis 时静默不装配，不能让上下文启动失败。 */
    @Test
    void nothing_is_registered_without_redis() {
        runner.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx).doesNotHaveBean(RepeatSubmitAspect.class);
            assertThat(ctx).doesNotHaveBean(RateLimitAspect.class);
        });
    }
}
