package com.shepherd.boot.autoconfigure;

import com.shepherd.boot.web.advice.ResultBodyAdvice;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

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
}
