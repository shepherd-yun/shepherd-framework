package com.shepherdyun.boot.storage;

import lombok.Data;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "shepherd.storage")
public class StorageProperties implements InitializingBean {

    private boolean enabled = false;
    private StorageProvider provider = StorageProvider.S3;
    /** 仅 provider=S3 时生效。 */
    private StoragePreset preset = StoragePreset.AWS;

    private @Nullable String endpoint;
    private @Nullable String region;
    private @Nullable String bucket;
    private @Nullable String accessKey;
    private @Nullable String secretKey;
    /** 可选的 CDN 域名，用于 publicUrl()。 */
    private @Nullable String publicBaseUrl;
    private Duration presignDefaultTtl = Duration.ofMinutes(15);

    /** 以下三项为 null 时取 preset 的值，非 null 时覆盖 preset。 */
    private @Nullable Boolean pathStyleAccess;
    private @Nullable Boolean chunkedEncoding;
    private @Nullable Boolean checksumCompatMode;

    public boolean resolvePathStyleAccess() {
        return pathStyleAccess != null ? pathStyleAccess : preset.isPathStyleAccess();
    }

    public boolean resolveChunkedEncoding() {
        return chunkedEncoding != null ? chunkedEncoding : preset.isChunkedEncoding();
    }

    public boolean resolveChecksumCompatMode() {
        return checksumCompatMode != null ? checksumCompatMode : preset.isChecksumCompatMode();
    }

    @Override
    public void afterPropertiesSet() {
        validate();
    }

    public void validate() {
        if (!enabled) {
            return;
        }
        requireText(endpoint, "shepherd.storage.endpoint");
        requireText(bucket, "shepherd.storage.bucket");
        requireText(accessKey, "shepherd.storage.access-key");
        requireText(secretKey, "shepherd.storage.secret-key");
        validateEndpointMatchesProvider();
    }

    /**
     * 阿里云两种模式的端点格式不同，填反时报出的是 SignatureDoesNotMatch 或连接失败，
     * 完全看不出真因，故在启动时拦下。
     */
    private void validateEndpointMatchesProvider() {
        String host = URI.create(endpoint).getHost();
        if (host == null) {
            throw new IllegalStateException(
                    "shepherd.storage.endpoint 不是合法 URL，应形如 https://host：" + endpoint);
        }
        if (!host.endsWith("aliyuncs.com")) {
            return;
        }
        boolean s3StylePrefix = host.startsWith("s3.");
        if (provider == StorageProvider.S3 && !s3StylePrefix) {
            throw new IllegalStateException(
                    "provider=s3 访问阿里云需使用 S3 兼容端点（带 s3. 前缀），例如 "
                    + "https://s3.oss-cn-hangzhou.aliyuncs.com；当前为 " + endpoint);
        }
        if (provider == StorageProvider.ALIYUN && s3StylePrefix) {
            throw new IllegalStateException(
                    "provider=aliyun 需使用原生端点（不带 s3. 前缀），例如 "
                    + "https://oss-cn-hangzhou.aliyuncs.com；当前为 " + endpoint);
        }
    }

    private static void requireText(@Nullable String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " 未配置（shepherd.storage.enabled=true 时必填）");
        }
    }
}
