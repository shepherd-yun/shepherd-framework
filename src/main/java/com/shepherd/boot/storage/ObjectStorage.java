package com.shepherd.boot.storage;

import org.jspecify.annotations.Nullable;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;

/**
 * 对象存储通用操作。分片上传由预签名直传绕过（浏览器直传，不经应用服务器）；
 * 桶管理与厂商特有能力（图片处理、生命周期规则）不在此接口内——
 * 需要时直接注入底层客户端 Bean（S3Client / OSS）。
 */
public interface ObjectStorage {

    ObjectRef put(String key, InputStream in, long size, @Nullable String contentType);

    ObjectRef put(String key, byte[] bytes, @Nullable String contentType);

    /** 调用方负责关闭返回的流。 */
    InputStream get(String key);

    boolean exists(String key);

    void delete(String key);

    URI presignGet(String key, Duration ttl);

    PresignedUpload presignPut(String key, Duration ttl, @Nullable String contentType);

    /** 基于 public-base-url 拼出的公开访问地址；未配置该项时回落为默认 TTL 的预签名 GET。 */
    String publicUrl(String key);
}
