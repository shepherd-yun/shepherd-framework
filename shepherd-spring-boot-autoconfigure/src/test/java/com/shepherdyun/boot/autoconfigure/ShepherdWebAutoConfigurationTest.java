package com.shepherdyun.boot.autoconfigure;

import com.shepherdyun.boot.web.advice.ResultBodyAdvice;
import com.shepherdyun.boot.web.async.MdcTaskDecorator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;

class ShepherdWebAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    JacksonAutoConfiguration.class, ShepherdWebAutoConfiguration.class));

    @Test
    void result_body_advice_is_registered_by_default() {
        runner.run(ctx -> assertThat(ctx).hasSingleBean(ResultBodyAdvice.class));
    }

    @Test
    void result_body_advice_can_be_disabled() {
        runner.withPropertyValues("shepherd.web.wrap-result.enabled=false")
              .run(ctx -> assertThat(ctx).doesNotHaveBean(ResultBodyAdvice.class));
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomDecoratorConfig {
        @Bean
        TaskDecorator customTaskDecorator() {
            return runnable -> runnable;
        }
    }

    @Test
    void mdc_task_decorator_is_registered_by_default() {
        runner.run(ctx -> assertThat(ctx).hasSingleBean(MdcTaskDecorator.class));
    }

    @Test
    void mdc_task_decorator_can_be_disabled() {
        runner.withPropertyValues("shepherd.web.async-mdc.enabled=false")
              .run(ctx -> assertThat(ctx).doesNotHaveBean(MdcTaskDecorator.class));
    }

    /** 两个 TaskDecorator 并存会让 Boot 静默跳过，所以 Starter 必须退让而非并存。 */
    @Test
    void starter_decorator_stands_down_for_a_downstream_one() {
        runner.withUserConfiguration(CustomDecoratorConfig.class)
              .run(ctx -> {
                  assertThat(ctx).doesNotHaveBean(MdcTaskDecorator.class);
                  assertThat(ctx).hasSingleBean(TaskDecorator.class);
              });
    }

    @Test
    void cors_source_is_absent_unless_explicitly_enabled() {
        runner.run(ctx -> assertThat(ctx).doesNotHaveBean(CorsConfigurationSource.class));
    }

    @Test
    void cors_source_is_registered_when_enabled() {
        runner.withPropertyValues(
                      "shepherd.web.cors.enabled=true",
                      "shepherd.web.cors.allowed-origins=https://app.example.com")
              .run(ctx -> assertThat(ctx).hasSingleBean(CorsConfigurationSource.class));
    }

    @Test
    void cors_context_fails_to_start_on_wildcard_plus_credentials() {
        runner.withPropertyValues(
                      "shepherd.web.cors.enabled=true",
                      "shepherd.web.cors.allowed-origins=*",
                      "shepherd.web.cors.allow-credentials=true")
              .run(ctx -> assertThat(ctx).hasFailed());
    }
}
