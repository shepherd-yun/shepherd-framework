package com.shepherdyun.boot.storage.s3;

import com.shepherdyun.boot.storage.StorageProperties;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

public final class S3Clients {

    /** endpointOverride 已指定具体主机时 region 仅参与签名；未配置时给一个占位值。 */
    private static final String DEFAULT_REGION = "us-east-1";

    private S3Clients() {}

    public static S3Client create(StorageProperties p) {
        boolean compat = p.resolveChecksumCompatMode();
        return S3Client.builder()
                .credentialsProvider(credentials(p))
                .region(region(p))
                .endpointOverride(URI.create(p.getEndpoint()))
                .serviceConfiguration(serviceConfiguration(p))
                // 默认的 WHEN_SUPPORTED 会附加 CRC32 校验头，R2 / MinIO / OSS 等
                // S3 兼容服务会因此报错，故兼容模式下降为 WHEN_REQUIRED。
                .requestChecksumCalculation(compat
                        ? RequestChecksumCalculation.WHEN_REQUIRED
                        : RequestChecksumCalculation.WHEN_SUPPORTED)
                .responseChecksumValidation(compat
                        ? ResponseChecksumValidation.WHEN_REQUIRED
                        : ResponseChecksumValidation.WHEN_SUPPORTED)
                .build();
    }

    public static S3Presigner createPresigner(StorageProperties p) {
        return S3Presigner.builder()
                .credentialsProvider(credentials(p))
                .region(region(p))
                .endpointOverride(URI.create(p.getEndpoint()))
                .serviceConfiguration(serviceConfiguration(p))
                .build();
    }

    private static S3Configuration serviceConfiguration(StorageProperties p) {
        return S3Configuration.builder()
                .pathStyleAccessEnabled(p.resolvePathStyleAccess())
                .chunkedEncodingEnabled(p.resolveChunkedEncoding())
                .build();
    }

    private static Region region(StorageProperties p) {
        String r = p.getRegion();
        return Region.of(r == null || r.isBlank() ? DEFAULT_REGION : r);
    }

    private static StaticCredentialsProvider credentials(StorageProperties p) {
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(p.getAccessKey(), p.getSecretKey()));
    }
}
