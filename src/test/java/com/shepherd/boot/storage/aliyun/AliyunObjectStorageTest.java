package com.shepherd.boot.storage.aliyun;

import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSS;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectResult;
import com.shepherd.boot.storage.ObjectRef;
import com.shepherd.boot.storage.StorageErrorCode;
import com.shepherd.boot.storage.StorageException;
import com.shepherd.boot.storage.StorageProperties;
import com.shepherd.boot.storage.StorageProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AliyunObjectStorageTest {

    private OSS oss;
    private AliyunObjectStorage storage;

    @BeforeEach
    void setUp() {
        oss = mock(OSS.class);
        StorageProperties props = new StorageProperties();
        props.setEnabled(true);
        props.setProvider(StorageProvider.ALIYUN);
        props.setEndpoint("https://oss-cn-hangzhou.aliyuncs.com");
        props.setBucket("my-bucket");
        props.setAccessKey("ak");
        props.setSecretKey("sk");
        storage = new AliyunObjectStorage(oss, props);
    }

    @Test
    void put_normalizes_key_before_calling_sdk() {
        PutObjectResult result = mock(PutObjectResult.class);
        when(result.getETag()).thenReturn("etag-1");
        when(oss.putObject(eq("my-bucket"), eq("a/b.txt"), any(InputStream.class), any(ObjectMetadata.class)))
                .thenReturn(result);

        ObjectRef ref = storage.put("/a/b.txt", "hi".getBytes(), "text/plain");

        assertThat(ref.key()).isEqualTo("a/b.txt");
        assertThat(ref.eTag()).isEqualTo("etag-1");
        assertThat(ref.size()).isEqualTo(2);
    }

    @Test
    void exists_delegates_to_sdk_with_normalized_key() {
        when(oss.doesObjectExist("my-bucket", "a/b.txt")).thenReturn(true);

        assertThat(storage.exists("//a/b.txt")).isTrue();
    }

    @Test
    void delete_delegates_to_sdk() {
        storage.delete("/a/b.txt");

        verify(oss).deleteObject("my-bucket", "a/b.txt");
    }

    @Test
    void sdk_failure_is_translated_to_storage_exception() {
        when(oss.doesObjectExist("my-bucket", "a.txt"))
                .thenThrow(new ClientException("boom"));

        assertThatThrownBy(() -> storage.exists("a.txt"))
                .isInstanceOf(StorageException.class)
                .extracting(e -> ((StorageException) e).getErrorCode())
                .isEqualTo(StorageErrorCode.DOWNLOAD_FAILED);
    }

    @Test
    void path_traversal_is_rejected_before_reaching_sdk() {
        assertThatThrownBy(() -> storage.delete("a/../../secret"))
                .isInstanceOf(StorageException.class);
        verify(oss, org.mockito.Mockito.never()).deleteObject(any(String.class), any(String.class));
    }
}
