package com.shepherdyun.boot.web.code;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CommonResultCodeTest {

    @Test
    void success_is_zero() {
        assertThat(CommonResultCode.SUCCESS.getCode()).isZero();
    }

    /**
     * 分段约定：每段低两位 00~49 归 Starter、50~99 归业务。
     * 这个断言是防撞码的唯一自动化守卫——低两位一旦爬进 50+ 就会与下游业务码冲突。
     */
    @Test
    void every_starter_code_reserves_the_lower_half_of_its_segment() {
        for (CommonResultCode rc : CommonResultCode.values()) {
            if (rc == CommonResultCode.SUCCESS) {
                continue;
            }
            assertThat(rc.getCode() % 100)
                    .as("%s(%d) 低两位必须落在 00~49", rc.name(), rc.getCode())
                    .isBetween(0, 49);
        }
    }

    @Test
    void every_code_carries_an_http_status_prefix() {
        for (CommonResultCode rc : CommonResultCode.values()) {
            if (rc == CommonResultCode.SUCCESS) {
                continue;
            }
            int prefix = rc.getCode() / 100;
            assertThat(prefix)
                    .as("%s(%d) 前缀必须是合法 HTTP 状态段", rc.name(), rc.getCode())
                    .isBetween(400, 599);
        }
    }

    @Test
    void auth_expired_codes_sit_in_the_starter_half_of_401() {
        // 前端按 40100~40149 判定「需登出」，这三个码必须落在该区间内
        assertThat(CommonResultCode.UNAUTHORIZED.getCode()).isBetween(40100, 40149);
        assertThat(CommonResultCode.TOKEN_INVALID.getCode()).isBetween(40100, 40149);
        assertThat(CommonResultCode.TOKEN_EXPIRED.getCode()).isBetween(40100, 40149);
    }

    @Test
    void codes_are_unique() {
        long distinct = java.util.Arrays.stream(CommonResultCode.values())
                .mapToInt(CommonResultCode::getCode)
                .distinct()
                .count();
        assertThat(distinct).isEqualTo(CommonResultCode.values().length);
    }
}
