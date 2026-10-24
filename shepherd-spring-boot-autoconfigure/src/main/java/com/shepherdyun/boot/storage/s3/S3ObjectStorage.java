package com.shepherdyun.boot.storage.s3;

import com.shepherdyun.boot.storage.*;
import org.jspecify.annotations.Nullable;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class S3ObjectStorage implements ObjectStorage {

    private final S3Client client;
    private final S3Presigner presigner;
    private final StorageProperties properties;

    public S3ObjectStorage(S3Client client, S3Presigner presigner, StorageProperties properties) {
        this.client = client;
        this.presigner = presigner;
        this.properties = properties;
    }

    private String bucket() {
        return properties.getBucket();
    }

    @Override
    public ObjectRef put(String key, InputStream in, long size, @Nullable String contentType) {
        String k = ObjectKeys.normalize(key);
        try {
            PutObjectRequest.Builder req = PutObjectRequest.builder().bucket(bucket()).key(k);
            if (contentType != null) {
                req.contentType(contentType);
            }
            PutObjectResponse res = client.putObject(req.build(), RequestBody.fromInputStream(in, size));
            return new ObjectRef(bucket(), k, res.eTag(), size);
        } catch (SdkException e) {
            throw new StorageException(StorageErrorCode.UPLOAD_FAILED, "上传失败：" + k, e);
        }
    }

    @Override
    public ObjectRef put(String key, byte[] bytes, @Nullable String contentType) {
        return put(key, new ByteArrayInputStream(bytes), bytes.length, contentType);
    }

    @Override
    public InputStream get(String key) {
        String k = ObjectKeys.normalize(key);
        try {
            ResponseInputStream<GetObjectResponse> stream =
                    client.getObject(GetObjectRequest.builder().bucket(bucket()).key(k).build());
            return stream;
        } catch (NoSuchKeyException e) {
            throw new StorageException(StorageErrorCode.OBJECT_NOT_FOUND, "对象不存在：" + k, e);
        } catch (SdkException e) {
            throw new StorageException(StorageErrorCode.DOWNLOAD_FAILED, "下载失败：" + k, e);
        }
    }

    @Override
    public boolean exists(String key) {
        String k = ObjectKeys.normalize(key);
        try {
            client.headObject(HeadObjectRequest.builder().bucket(bucket()).key(k).build());
            return true;
        } catch (NoSuchKeyException | NoSuchBucketException e) {
            return false;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            throw new StorageException(StorageErrorCode.DOWNLOAD_FAILED, "查询对象失败：" + k, e);
        }
    }

    @Override
    public void delete(String key) {
        String k = ObjectKeys.normalize(key);
        try {
            client.deleteObject(DeleteObjectRequest.builder().bucket(bucket()).key(k).build());
        } catch (SdkException e) {
            throw new StorageException(StorageErrorCode.DELETE_FAILED, "删除失败：" + k, e);
        }
    }

    @Override
    public URI presignGet(String key, Duration ttl) {
        String k = ObjectKeys.normalize(key);
        try {
            return presigner.presignGetObject(r -> r
                            .signatureDuration(ttl)
                            .getObjectRequest(g -> g.bucket(bucket()).key(k)))
                    .url().toURI();
        } catch (URISyntaxException | SdkException e) {
            throw new StorageException(StorageErrorCode.PRESIGN_FAILED, "生成预签名 GET 失败：" + k, e);
        }
    }

    @Override
    public PresignedUpload presignPut(String key, Duration ttl, @Nullable String contentType) {
        String k = ObjectKeys.normalize(key);
        try {
            PresignedPutObjectRequest presigned = presigner.presignPutObject(r -> r
                    .signatureDuration(ttl)
                    .putObjectRequest(p -> {
                        p.bucket(bucket()).key(k);
                        if (contentType != null) {
                            p.contentType(contentType);
                        }
                    }));
            return new PresignedUpload(presigned.url().toURI(), requiredHeaders(presigned.signedHeaders()));
        } catch (URISyntaxException | SdkException e) {
            throw new StorageException(StorageErrorCode.PRESIGN_FAILED, "生成预签名 PUT 失败：" + k, e);
        }
    }

    /** 剔除 host（浏览器自动填），其余签名头前端必须原样带上，否则 403。 */
    private static Map<String, String> requiredHeaders(Map<String, List<String>> signedHeaders) {
        Map<String, String> headers = new LinkedHashMap<>();
        signedHeaders.forEach((name, values) -> {
            if (!"host".equalsIgnoreCase(name) && !values.isEmpty()) {
                headers.put(name, values.getFirst());
            }
        });
        return Map.copyOf(headers);
    }

    @Override
    public String publicUrl(String key) {
        String k = ObjectKeys.normalize(key);
        String base = properties.getPublicBaseUrl();
        if (base == null || base.isBlank()) {
            return presignGet(k, properties.getPresignDefaultTtl()).toString();
        }
        return base.endsWith("/") ? base + k : base + "/" + k;
    }
}
