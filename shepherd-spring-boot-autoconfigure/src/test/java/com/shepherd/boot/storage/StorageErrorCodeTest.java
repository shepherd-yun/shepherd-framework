package com.shepherd.boot.storage;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StorageErrorCodeTest {

    @Test
    void codes_are_classified_by_http_semantics_not_by_module() {
        // key 非法是调用方的参数问题
        assertThat(StorageErrorCode.INVALID_KEY.getCode()).isEqualTo(40003);
        // 对象不存在是 404 语义
        assertThat(StorageErrorCode.OBJECT_NOT_FOUND.getCode()).isEqualTo(40401);
        // 其余是服务端 / 下游存储故障
        assertThat(StorageErrorCode.UPLOAD_FAILED.getCode()).isEqualTo(50001);
        assertThat(StorageErrorCode.DOWNLOAD_FAILED.getCode()).isEqualTo(50002);
        assertThat(StorageErrorCode.DELETE_FAILED.getCode()).isEqualTo(50003);
        assertThat(StorageErrorCode.PRESIGN_FAILED.getCode()).isEqualTo(50004);
    }

    @Test
    void every_code_reserves_the_starter_half_of_its_segment() {
        for (StorageErrorCode rc : StorageErrorCode.values()) {
            assertThat(rc.getCode() % 100)
                    .as("%s(%d) 低两位必须落在 00~49", rc.name(), rc.getCode())
                    .isBetween(0, 49);
        }
    }
}
