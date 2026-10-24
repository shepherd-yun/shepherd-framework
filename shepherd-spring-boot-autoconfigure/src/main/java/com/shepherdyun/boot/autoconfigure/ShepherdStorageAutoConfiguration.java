package com.shepherdyun.boot.autoconfigure;

import com.shepherdyun.boot.storage.ObjectStorage;
import com.shepherdyun.boot.storage.StorageProperties;
import com.shepherdyun.boot.storage.s3.S3Clients;
import com.shepherdyun.boot.storage.s3.S3ObjectStorage;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@AutoConfiguration
@ConditionalOnProperty(prefix = "shepherd.storage", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(StorageProperties.class)
public class ShepherdStorageAutoConfiguration {

    /**
     * 缺依赖时的 fail-fast。provider=s3/aliyun 但对应 SDK 不在 classpath 时，两个嵌套
     * @Configuration 都会被 @ConditionalOnClass 静默跳过，若不兜底，应用会正常启动，
     * 直到某个业务类注入 ObjectStorage 时才报一个难懂的 NoSuchBeanDefinitionException。
     * 嵌套 @Configuration 类在配置解析阶段先于外层类自身的 @Bean 方法被处理并登记，
     * 依赖齐全时其 ObjectStorage Bean 先注册，本方法的 @ConditionalOnMissingBean 因而
     * 不会触发（已用 ApplicationContextRunner 验证，见 ShepherdStorageAutoConfigurationTest）。
     */
    @Bean
    @ConditionalOnMissingBean(ObjectStorage.class)
    public ObjectStorage missingObjectStorage(StorageProperties properties) {
        String required = switch (properties.getProvider()) {
            case S3 -> "software.amazon.awssdk:s3 与 software.amazon.awssdk:apache-client";
            case ALIYUN -> "com.aliyun.oss:aliyun-sdk-oss";
        };
        throw new IllegalStateException(
                "对象存储已启用（shepherd.storage.provider=" + properties.getProvider()
                + "），但 classpath 上缺少对应 SDK：需要引入 " + required + "。"
                + "或设置 shepherd.storage.enabled=false 关闭对象存储。");
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(S3Client.class)
    @ConditionalOnProperty(prefix = "shepherd.storage", name = "provider", havingValue = "s3", matchIfMissing = true)
    static class S3StorageConfiguration {

        @Bean(destroyMethod = "close")
        @ConditionalOnMissingBean
        S3Client s3Client(StorageProperties properties) {
            return S3Clients.create(properties);
        }

        @Bean(destroyMethod = "close")
        @ConditionalOnMissingBean
        S3Presigner s3Presigner(StorageProperties properties) {
            return S3Clients.createPresigner(properties);
        }

        @Bean
        @ConditionalOnMissingBean(ObjectStorage.class)
        ObjectStorage s3ObjectStorage(S3Client client, S3Presigner presigner, StorageProperties properties) {
            return new S3ObjectStorage(client, presigner, properties);
        }
    }

    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(com.aliyun.oss.OSS.class)
    @ConditionalOnProperty(prefix = "shepherd.storage", name = "provider", havingValue = "aliyun")
    static class AliyunConfiguration {

        @Bean(destroyMethod = "shutdown")
        @ConditionalOnMissingBean
        com.aliyun.oss.OSS ossClient(StorageProperties properties) {
            return new com.aliyun.oss.OSSClientBuilder()
                    .build(properties.getEndpoint(), properties.getAccessKey(), properties.getSecretKey());
        }

        @Bean
        @ConditionalOnMissingBean(ObjectStorage.class)
        ObjectStorage aliyunObjectStorage(com.aliyun.oss.OSS oss, StorageProperties properties) {
            return new com.shepherdyun.boot.storage.aliyun.AliyunObjectStorage(oss, properties);
        }
    }
}
