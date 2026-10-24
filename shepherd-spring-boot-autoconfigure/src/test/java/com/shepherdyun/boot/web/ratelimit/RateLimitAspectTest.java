package com.shepherdyun.boot.web.ratelimit;

import com.shepherdyun.boot.web.code.CommonResultCode;
import com.shepherdyun.boot.web.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimitAspectTest {

    /** 内存假实现：按 key 计数，同时记录窗口，供断言 period/unit 是否被正确换算。 */
    static class FakeLimiter implements RateLimiter {
        final Map<String, Long> counts = new HashMap<>();
        final Map<String, Duration> windows = new LinkedHashMap<>();

        @Override
        public long increment(String key, Duration window) {
            windows.put(key, window);
            return counts.merge(key, 1L, Long::sum);
        }
    }

    static class Target {
        @RateLimit(count = 2, period = 30, unit = TimeUnit.SECONDS, dimension = RateLimit.Dimension.IP)
        String byIp() { return "ok"; }

        @RateLimit(count = 1, dimension = RateLimit.Dimension.GLOBAL, message = "挤爆了")
        String global() { return "ok"; }

        String unguarded() { return "free"; }

        @RateLimit(count = 1, dimension = RateLimit.Dimension.KEY, key = "#phone")
        String sendCode(String phone) { return "sent"; }

        @RateLimit(count = 1, dimension = RateLimit.Dimension.KEY, key = "#req.phone")
        String sendCodeByBody(SmsRequest req) { return "sent"; }

        @RateLimit(count = 3, period = 60, dimension = RateLimit.Dimension.KEY, key = "#phone")
        @RateLimit(count = 2, period = 1, unit = TimeUnit.DAYS, dimension = RateLimit.Dimension.KEY, key = "#phone")
        String sendCodeTwoWindows(String phone) { return "sent"; }

        @RateLimit(dimension = RateLimit.Dimension.KEY)
        String keyDimensionWithoutExpression(String phone) { return "sent"; }

        @RateLimit(key = "#phone")
        String expressionWithoutKeyDimension(String phone) { return "sent"; }
    }

    record SmsRequest(@org.jspecify.annotations.Nullable String phone) {}

    private FakeLimiter limiter;
    private Target proxy;

    @BeforeEach
    void setUp() {
        limiter = new FakeLimiter();
        AspectJProxyFactory factory = new AspectJProxyFactory(new Target());
        factory.addAspect(new RateLimitAspect(limiter));
        proxy = factory.getProxy();

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/things");
        request.setRemoteAddr("203.0.113.7");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    void calls_within_the_limit_pass_through() {
        assertThat(proxy.byIp()).isEqualTo("ok");
        assertThat(proxy.byIp()).isEqualTo("ok");
    }

    @Test
    void exceeding_the_limit_is_rejected_with_rate_limited_code() {
        proxy.byIp();
        proxy.byIp();

        assertThatThrownBy(proxy::byIp)
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode().getCode())
                        .isEqualTo(CommonResultCode.RATE_LIMITED.getCode()));
    }

    @Test
    void period_and_unit_are_converted_to_the_window() {
        proxy.byIp();

        assertThat(limiter.windows.values()).containsExactly(Duration.ofSeconds(30));
    }

    @Test
    void ip_dimension_key_contains_the_client_ip() {
        proxy.byIp();

        assertThat(limiter.counts.keySet()).allSatisfy(k -> assertThat(k).contains("203.0.113.7"));
    }

    @Test
    void global_dimension_key_is_shared_across_clients() {
        proxy.global();

        assertThat(limiter.counts.keySet()).allSatisfy(k -> assertThat(k).endsWith(":global"));
    }

    @Test
    void custom_message_is_used_when_provided() {
        proxy.global();

        assertThatThrownBy(proxy::global)
                .isInstanceOf(BizException.class)
                .hasMessage("挤爆了");
    }

    @Test
    void key_dimension_counts_each_expression_value_separately() {
        proxy.sendCode("13800000001");

        assertThat(proxy.sendCode("13800000002")).isEqualTo("sent");
        assertThatThrownBy(() -> proxy.sendCode("13800000001"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode().getCode())
                        .isEqualTo(CommonResultCode.RATE_LIMITED.getCode()));
    }

    @Test
    void key_expression_can_read_a_property_of_a_request_body() {
        proxy.sendCodeByBody(new SmsRequest("13800000001"));

        assertThat(proxy.sendCodeByBody(new SmsRequest("13800000002"))).isEqualTo("sent");
        assertThatThrownBy(() -> proxy.sendCodeByBody(new SmsRequest("13800000001")))
                .isInstanceOf(BizException.class);
    }

    /** 值来自请求参数：手机号这类个人信息不该明文躺在 Redis 里，任意长度的输入也不该原样变成 key。 */
    @Test
    void key_value_is_hashed_before_it_enters_the_redis_key() {
        proxy.sendCode("13800000001");

        assertThat(limiter.counts.keySet()).singleElement()
                .satisfies(k -> assertThat(k).doesNotContain("13800000001").contains(":key:"));
    }

    /** 与 USER 维度取不到登录态时同理：全部落进同一个「空值」计数器会让互不相干的请求互相误伤。 */
    @Test
    void null_key_value_falls_back_to_client_ip() {
        proxy.sendCodeByBody(new SmsRequest(null));

        assertThat(limiter.counts.keySet()).singleElement()
                .satisfies(k -> assertThat(k).endsWith(":ip:203.0.113.7"));
    }

    @Test
    void blank_key_value_falls_back_to_client_ip() {
        proxy.sendCode("  ");

        assertThat(limiter.counts.keySet()).singleElement()
                .satisfies(k -> assertThat(k).endsWith(":ip:203.0.113.7"));
    }

    @Test
    void stacked_limits_each_keep_their_own_window_and_counter() {
        proxy.sendCodeTwoWindows("13800000001");

        assertThat(limiter.windows.values())
                .containsExactlyInAnyOrder(Duration.ofSeconds(60), Duration.ofDays(1));
    }

    @Test
    void the_tighter_of_stacked_limits_rejects_even_when_the_other_still_has_room() {
        proxy.sendCodeTwoWindows("13800000001");
        proxy.sendCodeTwoWindows("13800000001");

        // 60 秒窗口允许 3 次，还有余量；按天的窗口只允许 2 次，第三次被它拦下
        assertThatThrownBy(() -> proxy.sendCodeTwoWindows("13800000001"))
                .isInstanceOf(BizException.class);
    }

    @Test
    void key_dimension_without_an_expression_fails_loudly() {
        assertThatThrownBy(() -> proxy.keyDimensionWithoutExpression("13800000001"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("key");
    }

    /** 写了 key 却忘了改 dimension，若静默按 IP 计数，「每个手机号 1 次」会悄悄变成「每个 IP 1 次」。 */
    @Test
    void key_expression_with_another_dimension_fails_loudly() {
        assertThatThrownBy(() -> proxy.expressionWithoutKeyDimension("13800000001"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Dimension.KEY");
    }

    @Test
    void unannotated_method_is_untouched() {
        assertThat(proxy.unguarded()).isEqualTo("free");
        assertThat(limiter.counts).isEmpty();
    }
}
