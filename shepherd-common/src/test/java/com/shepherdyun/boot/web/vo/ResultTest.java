package com.shepherdyun.boot.web.vo;

import com.shepherdyun.boot.web.code.CommonResultCode;
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
        assertThat(r.getCode()).isEqualTo(40100);
        assertThat(r.getMessage()).isEqualTo("未认证或登录已失效");
        assertThat(r.getData()).isNull();
    }

    @Test
    void token_codes_sit_in_the_auth_expired_range() {
        // 前端按区间 40100~40149 判定「凭证失效需登出」，这两个码必须落在其中
        assertThat(CommonResultCode.TOKEN_INVALID.getCode()).isEqualTo(40101);
        assertThat(CommonResultCode.TOKEN_EXPIRED.getCode()).isEqualTo(40102);
    }
}
