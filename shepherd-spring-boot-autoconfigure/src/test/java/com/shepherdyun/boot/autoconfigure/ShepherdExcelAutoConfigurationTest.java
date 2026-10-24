package com.shepherdyun.boot.autoconfigure;

import com.shepherdyun.boot.excel.AsyncExcelService;
import com.shepherdyun.boot.excel.ExcelImporter;
import com.shepherdyun.boot.excel.ExcelTask;
import com.shepherdyun.boot.excel.ExcelTaskController;
import com.shepherdyun.boot.excel.ExcelTaskStore;
import com.shepherdyun.boot.excel.RedisExcelTaskStore;
import com.shepherdyun.boot.serialno.SerialNoGenerator;
import com.shepherdyun.boot.storage.ObjectStorage;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ShepherdExcelAutoConfigurationTest {

    @Configuration(proxyBeanMethods = false)
    static class AsyncDepsPresent {
        @Bean StringRedisTemplate stringRedisTemplate() { return mock(StringRedisTemplate.class); }
        @Bean ObjectStorage objectStorage() { return mock(ObjectStorage.class); }
        @Bean SerialNoGenerator serialNoGenerator() { return mock(SerialNoGenerator.class); }
        @Bean JsonMapper jsonMapper() { return JsonMapper.builder().build(); }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomStorePresent {
        @Bean StringRedisTemplate stringRedisTemplate() { return mock(StringRedisTemplate.class); }
        @Bean ObjectStorage objectStorage() { return mock(ObjectStorage.class); }
        @Bean SerialNoGenerator serialNoGenerator() { return mock(SerialNoGenerator.class); }
        @Bean JsonMapper jsonMapper() { return JsonMapper.builder().build(); }

        @Bean
        ExcelTaskStore customStore() {
            return new ExcelTaskStore() {
                @Override public void save(ExcelTask task) { }
                @Override public Optional<ExcelTask> find(String id) { return Optional.empty(); }
            };
        }
    }

    /** Redis 在位（= 打算用异步）但没有对象存储承载结果文件。 */
    @Configuration(proxyBeanMethods = false)
    static class StorageMissing {
        @Bean StringRedisTemplate stringRedisTemplate() { return mock(StringRedisTemplate.class); }
        @Bean SerialNoGenerator serialNoGenerator() { return mock(SerialNoGenerator.class); }
        @Bean JsonMapper jsonMapper() { return JsonMapper.builder().build(); }
    }

    @Configuration(proxyBeanMethods = false)
    static class SerialNoMissing {
        @Bean StringRedisTemplate stringRedisTemplate() { return mock(StringRedisTemplate.class); }
        @Bean ObjectStorage objectStorage() { return mock(ObjectStorage.class); }
        @Bean JsonMapper jsonMapper() { return JsonMapper.builder().build(); }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ShepherdExcelAutoConfiguration.class));

    @Test
    void nothing_is_wired_when_disabled() {
        runner.run(context -> assertThat(context).doesNotHaveBean(ExcelImporter.class));
    }

    @Test
    void sync_import_works_without_redis_storage_or_security() {
        runner.withPropertyValues("shepherd.excel.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(ExcelImporter.class);
                    assertThat(context).doesNotHaveBean(AsyncExcelService.class);
                });
    }

    @Test
    void async_beans_are_wired_when_every_dependency_is_present() {
        runner.withPropertyValues("shepherd.excel.enabled=true")
                .withUserConfiguration(AsyncDepsPresent.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(AsyncExcelService.class);
                    assertThat(context).hasSingleBean(ExcelTaskController.class);
                    assertThat(context.getBean(ExcelTaskStore.class))
                            .isInstanceOf(RedisExcelTaskStore.class);
                });
    }

    @Test
    void a_downstream_task_store_replaces_the_redis_default() {
        runner.withPropertyValues("shepherd.excel.enabled=true")
                .withUserConfiguration(CustomStorePresent.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(ExcelTaskStore.class);
                    assertThat(context.getBean(ExcelTaskStore.class))
                            .isNotInstanceOf(RedisExcelTaskStore.class);
                });
    }

    @Test
    void the_task_endpoint_can_be_turned_off() {
        runner.withPropertyValues("shepherd.excel.enabled=true",
                        "shepherd.excel.task.endpoint-enabled=false")
                .withUserConfiguration(AsyncDepsPresent.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(AsyncExcelService.class);
                    assertThat(context).doesNotHaveBean(ExcelTaskController.class);
                });
    }

    /** 缺依赖要在启动时说清楚缺什么，而不是留到运行时静默失效。 */
    @Test
    void missing_object_storage_fails_fast_and_says_what_is_missing() {
        runner.withPropertyValues("shepherd.excel.enabled=true")
                .withUserConfiguration(StorageMissing.class)
                .run(context -> assertThat(context).getFailure()
                        .hasStackTraceContaining("shepherd.storage"));
    }

    @Test
    void missing_serial_no_generator_fails_fast_and_says_what_is_missing() {
        runner.withPropertyValues("shepherd.excel.enabled=true")
                .withUserConfiguration(SerialNoMissing.class)
                .run(context -> assertThat(context).getFailure()
                        .hasStackTraceContaining("SerialNoGenerator"));
    }
}
