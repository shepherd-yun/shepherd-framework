package com.shepherd.boot.autoconfigure;

import com.shepherd.boot.storage.ObjectStorage;
import com.shepherd.boot.storage.StorageProperties;
import com.shepherd.boot.storage.s3.S3Clients;
import com.shepherd.boot.storage.s3.S3ObjectStorage;
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
            return new com.shepherd.boot.storage.aliyun.AliyunObjectStorage(oss, properties);
        }
    }
}
