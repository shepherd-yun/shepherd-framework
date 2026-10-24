package com.shepherdyun.boot.storage;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StoragePropertiesTest {

    private StorageProperties enabledProps() {
        StorageProperties p = new StorageProperties();
        p.setEnabled(true);
        p.setEndpoint("https://s3.oss-cn-hangzhou.aliyuncs.com");
        p.setBucket("my-bucket");
        p.setAccessKey("ak");
        p.setSecretKey("sk");
        p.setProvider(StorageProvider.S3);
        p.setPreset(StoragePreset.ALIYUN);
        return p;
    }

    @Test
    void disabled_skips_all_validation() {
        StorageProperties p = new StorageProperties();
        assertThatCode(p::validate).doesNotThrowAnyException();
    }

    @Test
    void enabled_requires_bucket() {
        StorageProperties p = enabledProps();
        p.setBucket(null);
        assertThatThrownBy(p::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("shepherd.storage.bucket");
    }

    @Test
    void aliyun_preset_disables_chunked_encoding_and_path_style() {
        StorageProperties p = enabledProps();
        assertThat(p.resolveChunkedEncoding()).isFalse();
        assertThat(p.resolvePathStyleAccess()).isFalse();
        assertThat(p.resolveChecksumCompatMode()).isTrue();
    }

    @Test
    void minio_preset_enables_path_style() {
        StorageProperties p = enabledProps();
        p.setPreset(StoragePreset.MINIO);
        assertThat(p.resolvePathStyleAccess()).isTrue();
    }

    @Test
    void aws_preset_keeps_full_checksum_validation() {
        StorageProperties p = enabledProps();
        p.setPreset(StoragePreset.AWS);
        assertThat(p.resolveChecksumCompatMode()).isFalse();
    }

    @Test
    void explicit_property_overrides_preset() {
        StorageProperties p = enabledProps();
        p.setPreset(StoragePreset.MINIO);
        p.setPathStyleAccess(false);
        assertThat(p.resolvePathStyleAccess()).isFalse();
    }

    @Test
    void s3_provider_rejects_aliyun_native_endpoint() {
        StorageProperties p = enabledProps();
        p.setEndpoint("https://oss-cn-hangzhou.aliyuncs.com");
        assertThatThrownBy(p::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("s3.");
    }

    @Test
    void aliyun_provider_rejects_s3_compatible_endpoint() {
        StorageProperties p = enabledProps();
        p.setProvider(StorageProvider.ALIYUN);
        assertThatThrownBy(p::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("原生端点");
    }

    @Test
    void non_aliyun_endpoints_are_not_prefix_checked() {
        StorageProperties p = enabledProps();
        p.setEndpoint("https://abc123.r2.cloudflarestorage.com");
        p.setPreset(StoragePreset.R2);
        assertThatCode(p::validate).doesNotThrowAnyException();
    }
}
