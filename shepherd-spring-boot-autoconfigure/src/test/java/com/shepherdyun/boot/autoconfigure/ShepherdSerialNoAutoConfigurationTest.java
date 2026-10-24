package com.shepherdyun.boot.autoconfigure;

import com.shepherdyun.boot.serialno.RedisSerialNoGenerator;
import com.shepherdyun.boot.serialno.SerialNoGenerator;
import com.shepherdyun.boot.serialno.SerialNoRule;
import com.shepherdyun.boot.serialno.ShepherdSerialNoProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ShepherdSerialNoAutoConfigurationTest {

    @Configuration(proxyBeanMethods = false)
    static class RedisPresent {
        @Bean
        StringRedisTemplate stringRedisTemplate() {
            return mock(StringRedisTemplate.class);
        }
    }

    static class CustomGenerator implements SerialNoGenerator {
        @Override
        public String next(SerialNoRule rule) {
            return "CUSTOM";
        }

        @Override
        public List<String> nextBatch(SerialNoRule rule, int count) {
            return List.of("CUSTOM");
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomGeneratorPresent {
        @Bean
        StringRedisTemplate stringRedisTemplate() {
            return mock(StringRedisTemplate.class);
        }

        @Bean
        SerialNoGenerator customGenerator() {
            return new CustomGenerator();
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ShepherdSerialNoAutoConfiguration.class));

    @Test
    void generator_is_registered_when_redis_is_present() {
        runner.withUserConfiguration(RedisPresent.class).run(ctx -> {
            assertThat(ctx).hasSingleBean(SerialNoGenerator.class);
            assertThat(ctx.getBean(SerialNoGenerator.class)).isInstanceOf(RedisSerialNoGenerator.class);
        });
    }

    /** 没有 Redis 时静默不装配，不能让上下文启动失败。 */
    @Test
    void nothing_is_registered_without_redis() {
        runner.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx).doesNotHaveBean(SerialNoGenerator.class);
        });
    }

    @Test
    void nothing_is_registered_when_disabled() {
        runner.withUserConfiguration(RedisPresent.class)
              .withPropertyValues("shepherd.serial-no.enabled=false")
              .run(ctx -> {
                  assertThat(ctx).hasNotFailed();
                  assertThat(ctx).doesNotHaveBean(SerialNoGenerator.class);
              });
    }

    @Test
    void downstream_bean_wins() {
        runner.withUserConfiguration(CustomGeneratorPresent.class).run(ctx -> {
            assertThat(ctx).hasSingleBean(SerialNoGenerator.class);
            assertThat(ctx.getBean(SerialNoGenerator.class)).isInstanceOf(CustomGenerator.class);
        });
    }

    @Test
    void key_prefix_is_configurable() {
        runner.withUserConfiguration(RedisPresent.class)
              .withPropertyValues("shepherd.serial-no.key-prefix=myapp:no:")
              .run(ctx -> {
                  assertThat(ctx).hasSingleBean(ShepherdSerialNoProperties.class);
                  assertThat(ctx.getBean(ShepherdSerialNoProperties.class).getKeyPrefix())
                          .isEqualTo("myapp:no:");
              });
    }
}
