package com.shepherdyun.boot.storage;

/**
 * S3 协议下各家服务商的兼容性预设（仅 provider=S3 时生效）。
 * 三个开关无人记得住，故用预设承载；yaml 中显式写的属性覆盖预设值。
 */
public enum StoragePreset {
    /** 阿里云 OSS 的 S3 兼容端点：官方要求 Java SDK 2.x 关闭 chunked encoding 并使用虚拟主机风格。 */
    ALIYUN(false, false, true),
    /** Cloudflare R2。 */
    R2(false, true, true),
    /** MinIO：必须路径风格。 */
    MINIO(true, true, true),
    /** 真 AWS S3：保留完整校验。 */
    AWS(false, true, false);

    private final boolean pathStyleAccess;
    private final boolean chunkedEncoding;
    private final boolean checksumCompatMode;

    StoragePreset(boolean pathStyleAccess, boolean chunkedEncoding, boolean checksumCompatMode) {
        this.pathStyleAccess = pathStyleAccess;
        this.chunkedEncoding = chunkedEncoding;
        this.checksumCompatMode = checksumCompatMode;
    }

    public boolean isPathStyleAccess() { return pathStyleAccess; }
    public boolean isChunkedEncoding() { return chunkedEncoding; }
    public boolean isChecksumCompatMode() { return checksumCompatMode; }
}
