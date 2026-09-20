package com.shepherd.boot.storage.aliyun;

import com.aliyun.oss.ClientException;
import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectResult;
import com.shepherd.boot.storage.*;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Date;
import java.util.Map;

public class AliyunObjectStorage implements ObjectStorage {

    private final OSS oss;
    private final StorageProperties properties;

    public AliyunObjectStorage(OSS oss, StorageProperties properties) {
        this.oss = oss;
        this.properties = properties;
    }

    private String bucket() {
        return properties.getBucket();
    }

    private static Date expiresAt(Duration ttl) {
        return new Date(System.currentTimeMillis() + ttl.toMillis());
    }

    @Override
    public ObjectRef put(String key, InputStream in, long size, @Nullable String contentType) {
        String k = ObjectKeys.normalize(key);
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(size);
            if (contentType != null) {
                metadata.setContentType(contentType);
            }
            PutObjectResult result = oss.putObject(bucket(), k, in, metadata);
            return new ObjectRef(bucket(), k, result.getETag(), size);
        } catch (OSSException | ClientException e) {
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
            return oss.getObject(bucket(), k).getObjectContent();
        } catch (OSSException e) {
            if ("NoSuchKey".equals(e.getErrorCode())) {
                throw new StorageException(StorageErrorCode.OBJECT_NOT_FOUND, "对象不存在：" + k, e);
            }
            throw new StorageException(StorageErrorCode.DOWNLOAD_FAILED, "下载失败：" + k, e);
        } catch (ClientException e) {
            throw new StorageException(StorageErrorCode.DOWNLOAD_FAILED, "下载失败：" + k, e);
        }
    }

    @Override
    public boolean exists(String key) {
        String k = ObjectKeys.normalize(key);
        try {
            return oss.doesObjectExist(bucket(), k);
        } catch (OSSException | ClientException e) {
            throw new StorageException(StorageErrorCode.DOWNLOAD_FAILED, "查询对象失败：" + k, e);
        }
    }

    @Override
    public void delete(String key) {
        String k = ObjectKeys.normalize(key);
        try {
            oss.deleteObject(bucket(), k);
        } catch (OSSException | ClientException e) {
            throw new StorageException(StorageErrorCode.DELETE_FAILED, "删除失败：" + k, e);
        }
    }

    @Override
    public URI presignGet(String key, Duration ttl) {
        String k = ObjectKeys.normalize(key);
        try {
            return oss.generatePresignedUrl(bucket(), k, expiresAt(ttl)).toURI();
        } catch (URISyntaxException | OSSException | ClientException e) {
            throw new StorageException(StorageErrorCode.PRESIGN_FAILED, "生成预签名 GET 失败：" + k, e);
        }
    }

    @Override
    public PresignedUpload presignPut(String key, Duration ttl, @Nullable String contentType) {
        String k = ObjectKeys.normalize(key);
        try {
            GeneratePresignedUrlRequest request =
                    new GeneratePresignedUrlRequest(bucket(), k, HttpMethod.PUT);
            request.setExpiration(expiresAt(ttl));
            if (contentType != null) {
                request.setContentType(contentType);
            }
            URI url = oss.generatePresignedUrl(request).toURI();
            // 签入了 Content-Type 时，前端 PUT 必须原样带上，否则 403。
            Map<String, String> required = contentType == null
                    ? Map.of()
                    : Map.of("Content-Type", contentType);
            return new PresignedUpload(url, required);
        } catch (URISyntaxException | OSSException | ClientException e) {
            throw new StorageException(StorageErrorCode.PRESIGN_FAILED, "生成预签名 PUT 失败：" + k, e);
        }
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
