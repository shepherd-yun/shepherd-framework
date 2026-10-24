package com.shepherdyun.boot.autoconfigure;

import com.aliyun.oss.OSS;
import com.shepherdyun.boot.storage.ObjectStorage;
import com.shepherdyun.boot.storage.aliyun.AliyunObjectStorage;
import com.shepherdyun.boot.storage.s3.S3ObjectStorage;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.services.s3.S3Client;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 覆盖 {@link ShepherdStorageAutoConfiguration} 的兜底 fail-fast Bean {@code missingObjectStorage}：
 * <ul>
 *   <li>依赖缺失时应快速失败并给出可操作的报错；</li>
 *   <li>依赖齐全时不应触发——用于真实验证外层 @Bean 方法与嵌套 @Configuration 类的
 *       {@code @ConditionalOnMissingBean} 注册顺序：嵌套配置类在配置解析阶段先于外层类自身的
 *       @Bean 方法被处理并登记 Bean 定义，因此依赖齐全时嵌套配置产出的真实 ObjectStorage Bean
 *       会先于兜底方法被登记，兜底方法的 @ConditionalOnMissingBean 条件不成立、不会触发。</li>
 * </ul>
 */
class ShepherdStorageAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ShepherdStorageAutoConfiguration.class));

    @Test
    void aliyun_provider_without_oss_sdk_on_classpath_fails_fast_with_guidance() {
        runner
                .withClassLoader(new FilteredClassLoader(OSS.class))
                .withPropertyValues(
                        "shepherd.storage.enabled=true",
                        "shepherd.storage.provider=aliyun",
                        "shepherd.storage.endpoint=https://oss-cn-hangzhou.aliyuncs.com",
                        "shepherd.storage.bucket=bucket",
                        "shepherd.storage.access-key=ak",
                        "shepherd.storage.secret-key=sk")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context).getFailure()
                            .hasStackTraceContaining("aliyun-sdk-oss")
                            .hasStackTraceContaining("shepherd.storage.enabled=false");
                });
    }

    @Test
    void s3_provider_without_awssdk_on_classpath_fails_fast_with_guidance() {
        runner
                .withClassLoader(new FilteredClassLoader(S3Client.class))
                .withPropertyValues(
                        "shepherd.storage.enabled=true",
                        "shepherd.storage.provider=s3",
                        "shepherd.storage.endpoint=https://example-endpoint.invalid",
                        "shepherd.storage.region=us-east-1",
                        "shepherd.storage.bucket=bucket",
                        "shepherd.storage.access-key=ak",
                        "shepherd.storage.secret-key=sk")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context).getFailure()
                            .hasStackTraceContaining("software.amazon.awssdk:s3")
                            .hasStackTraceContaining("shepherd.storage.enabled=false");
                });
    }

    @Test
    void s3_provider_with_dependency_present_gets_real_bean_and_fallback_is_not_triggered() {
        runner
                .withPropertyValues(
                        "shepherd.storage.enabled=true",
                        "shepherd.storage.provider=s3",
                        "shepherd.storage.endpoint=https://example-endpoint.invalid",
                        "shepherd.storage.region=us-east-1",
                        "shepherd.storage.bucket=bucket",
                        "shepherd.storage.access-key=ak",
                        "shepherd.storage.secret-key=sk")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ObjectStorage.class);
                    assertThat(context.getBean(ObjectStorage.class)).isInstanceOf(S3ObjectStorage.class);
                });
    }

    @Test
    void aliyun_provider_with_dependency_present_gets_real_bean_and_fallback_is_not_triggered() {
        runner
                .withPropertyValues(
                        "shepherd.storage.enabled=true",
                        "shepherd.storage.provider=aliyun",
                        "shepherd.storage.endpoint=https://oss-cn-hangzhou.aliyuncs.com",
                        "shepherd.storage.bucket=bucket",
                        "shepherd.storage.access-key=ak",
                        "shepherd.storage.secret-key=sk")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ObjectStorage.class);
                    assertThat(context.getBean(ObjectStorage.class)).isInstanceOf(AliyunObjectStorage.class);
                });
    }
}
