package com.shepherd.boot.web.ratelimit;

import com.shepherd.boot.web.code.CommonResultCode;
import com.shepherd.boot.web.exception.BizException;
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
    }

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
    void unannotated_method_is_untouched() {
        assertThat(proxy.unguarded()).isEqualTo("free");
        assertThat(limiter.counts).isEmpty();
    }
}
