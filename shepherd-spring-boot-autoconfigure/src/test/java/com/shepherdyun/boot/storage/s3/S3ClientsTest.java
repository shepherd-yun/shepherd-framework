package com.shepherdyun.boot.storage.s3;

import com.shepherdyun.boot.storage.StoragePreset;
import com.shepherdyun.boot.storage.StorageProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import static org.assertj.core.api.Assertions.assertThat;

class S3ClientsTest {

    private StorageProperties props(StoragePreset preset) {
        StorageProperties p = new StorageProperties();
        p.setEnabled(true);
        p.setPreset(preset);
        p.setEndpoint("https://example-endpoint.invalid");
        p.setRegion("auto");
        p.setBucket("bucket");
        p.setAccessKey("ak");
        p.setSecretKey("sk");
        return p;
    }

    @ParameterizedTest
    @EnumSource(StoragePreset.class)
    void every_preset_builds_a_client_and_presigner(StoragePreset preset) {
        try (S3Client client = S3Clients.create(props(preset));
             S3Presigner presigner = S3Clients.createPresigner(props(preset))) {
            assertThat(client).isNotNull();
            assertThat(presigner).isNotNull();
        }
    }

    @Test
    void blank_region_falls_back_to_a_placeholder() {
        StorageProperties p = props(StoragePreset.R2);
        p.setRegion(null);
        try (S3Client client = S3Clients.create(p)) {
            assertThat(client).isNotNull();
        }
    }
}
