package com.shepherd.boot.core.result;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ResultTest {

    @Test
    void success_wraps_data_with_zero_code() {
        Result<String> r = Result.success("hello");
        assertThat(r.getCode()).isZero();
        assertThat(r.getMessage()).isEqualTo("ok");
        assertThat(r.getData()).isEqualTo("hello");
    }

    @Test
    void fail_from_errorcode_carries_code_and_message() {
        Result<Void> r = Result.fail(CommonResultCode.UNAUTHORIZED);
        assertThat(r.getCode()).isEqualTo(10001);
        assertThat(r.getMessage()).isEqualTo("未认证或登录已失效");
        assertThat(r.getData()).isNull();
    }

    @Test
    void reserved_token_codes_keep_their_historical_values() {
        // 前端 AUTH_ERROR_CODES = [10001, 20001, 20002] 硬依赖这些数值
        assertThat(CommonResultCode.TOKEN_INVALID.getCode()).isEqualTo(20001);
        assertThat(CommonResultCode.TOKEN_EXPIRED.getCode()).isEqualTo(20002);
    }
}
