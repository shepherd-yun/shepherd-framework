package com.shepherdyun.boot.autoconfigure;

import cn.idev.excel.FastExcel;
import com.shepherdyun.boot.excel.AsyncExcelService;
import com.shepherdyun.boot.excel.ExcelImporter;
import com.shepherdyun.boot.excel.ExcelTaskController;
import com.shepherdyun.boot.excel.ExcelTaskStore;
import com.shepherdyun.boot.excel.RedisExcelTaskStore;
import com.shepherdyun.boot.excel.ShepherdExcelProperties;
import com.shepherdyun.boot.serialno.SerialNoGenerator;
import com.shepherdyun.boot.storage.ObjectStorage;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.json.JsonMapper;

import java.util.concurrent.Executor;

/**
 * Excel 导入导出装配。
 *
 * <p>默认关闭（{@code shepherd.excel.enabled=false}）：开启会拖进 POI + xmlbeans 约 18 个 jar。
 *
 * <p><b>同步与异步分两层装配</b>：{@link ExcelImporter} 无额外依赖，开启即有；
 * 异步能力以「容器中有 {@link StringRedisTemplate}」为启用信号——任务态存 Redis，
 * 没有 Redis 就谈不上异步。异步一旦启用，缺 {@link ObjectStorage} 或 {@link SerialNoGenerator}
 * 都会在启动时 fail-fast 并指明缺什么，而不是像静默失效那样留到运行时才炸。
 */
@AutoConfiguration
@ConditionalOnClass(FastExcel.class)
@ConditionalOnProperty(prefix = "shepherd.excel", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(ShepherdExcelProperties.class)
public class ShepherdExcelAutoConfiguration {

    /** 同步导入不依赖 Redis / 存储 / 安全，可独立使用。 */
    @Bean
    @ConditionalOnMissingBean
    public ExcelImporter excelImporter(ShepherdExcelProperties properties,
                                       ObjectProvider<Validator> validator) {
        return new ExcelImporter(properties, validator.getIfAvailable());
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass({StringRedisTemplate.class, ObjectStorage.class, SecurityContextHolder.class})
    @ConditionalOnBean(StringRedisTemplate.class)
    static class AsyncConfiguration {

        private static final Logger log = LoggerFactory.getLogger(AsyncConfiguration.class);

        @Bean
        @ConditionalOnMissingBean(ExcelTaskStore.class)
        ExcelTaskStore redisExcelTaskStore(StringRedisTemplate redis,
                                           ShepherdExcelProperties properties,
                                           ObjectProvider<JsonMapper> jsonMapper) {
            return new RedisExcelTaskStore(redis, properties,
                    jsonMapper.getIfAvailable(() -> JsonMapper.builder().build()));
        }

        @Bean
        @ConditionalOnMissingBean
        AsyncExcelService asyncExcelService(ExcelTaskStore store,
                                            ObjectProvider<ObjectStorage> storage,
                                            ObjectProvider<SerialNoGenerator> serialNo,
                                            ExcelImporter importer,
                                            ShepherdExcelProperties properties,
                                            ObjectProvider<AsyncTaskExecutor> taskExecutor) {
            ObjectStorage objectStorage = storage.getIfAvailable();
            if (objectStorage == null) {
                throw new IllegalStateException(
                        "Excel 异步导入导出需要对象存储来承载结果文件，但容器中没有 ObjectStorage Bean："
                        + "请设置 shepherd.storage.enabled=true 并配置 shepherd.storage.*。"
                        + "只用同步导入导出的话，去掉 StringRedisTemplate 或不调用 AsyncExcelService 即可。");
            }
            SerialNoGenerator generator = serialNo.getIfAvailable();
            if (generator == null) {
                throw new IllegalStateException(
                        "Excel 异步导入导出需要 SerialNoGenerator 发任务号，但容器中没有该 Bean："
                        + "请确认 shepherd.serial-no 未被关闭，且 spring-boot-starter-data-redis 在 classpath 上。");
            }
            return new AsyncExcelService(store, objectStorage, generator, importer, properties,
                    resolveExecutor(taskExecutor));
        }

        /**
         * 优先用 Boot 的 applicationTaskExecutor——MdcTaskDecorator 挂在它上面，traceId 才透传得过去。
         * 取不到唯一的一个时回落成独立线程池，并明确告知 traceId 会断。
         */
        private static Executor resolveExecutor(ObjectProvider<AsyncTaskExecutor> taskExecutor) {
            AsyncTaskExecutor executor = taskExecutor.getIfUnique();
            if (executor != null) {
                return executor;
            }
            log.warn("容器中没有唯一的 AsyncTaskExecutor，Excel 异步任务回落到独立线程池，"
                    + "日志将不带 traceId；如需透传请保留 Boot 的 applicationTaskExecutor。");
            return new SimpleAsyncTaskExecutor("shepherd-excel-");
        }

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnProperty(prefix = "shepherd.excel.task", name = "endpoint-enabled",
                havingValue = "true", matchIfMissing = true)
        ExcelTaskController excelTaskController(AsyncExcelService service) {
            return new ExcelTaskController(service);
        }
    }
}
