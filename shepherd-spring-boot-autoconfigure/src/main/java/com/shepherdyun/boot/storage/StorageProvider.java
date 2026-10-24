package com.shepherdyun.boot.storage;

public enum StorageProvider {
    /** 走 S3 协议，适配 AWS S3、Cloudflare R2、MinIO、腾讯 COS、阿里云 OSS 的 S3 兼容端点。 */
    S3,
    /** 走阿里云 OSS 原生 SDK。 */
    ALIYUN
}
