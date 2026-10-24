package com.shepherdyun.boot.autoconfigure;

import com.shepherdyun.boot.serialno.RedisSerialNoGenerator;
import com.shepherdyun.boot.serialno.SerialNoGenerator;
import com.shepherdyun.boot.serialno.ShepherdSerialNoProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 单号生成能力的装配。
 *
 * <p>不加 {@code @ConditionalOnWebApplication}——批处理、定时任务、MQ 消费者同样要发号。
 *
 * <p>无 Redis 时静默不装配，与 {@code @RateLimit} / {@code @RepeatSubmit} 一致的取舍：
 * 可选增强能力，不涉安全语义，没有 fail-fast 的理由。后果是下游注入 {@link SerialNoGenerator}
 * 时启动失败并提示缺 bean，足够自解释。
 */
@AutoConfiguration(afterName = "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration")
@ConditionalOnClass(StringRedisTemplate.class)
@ConditionalOnBean(StringRedisTemplate.class)
@ConditionalOnProperty(prefix = "shepherd.serial-no", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(ShepherdSerialNoProperties.class)
public class ShepherdSerialNoAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SerialNoGenerator.class)
    public SerialNoGenerator serialNoGenerator(StringRedisTemplate redis,
                                               ShepherdSerialNoProperties properties) {
        return new RedisSerialNoGenerator(redis, properties.getKeyPrefix());
    }
}
