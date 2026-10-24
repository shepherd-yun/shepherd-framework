package com.shepherdyun.boot.web.ratelimit;

import com.shepherdyun.boot.web.code.CommonResultCode;
import com.shepherdyun.boot.web.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RepeatSubmitAspectTest {

    /** 内存假实现：记录 key 与 ttl，第二次同 key 返回 false。 */
    static class FakeGuard implements RepeatSubmitGuard {
        final Map<String, Duration> acquired = new HashMap<>();

        @Override
        public boolean tryAcquire(String key, Duration ttl) {
            if (acquired.containsKey(key)) {
                return false;
            }
            acquired.put(key, ttl);
            return true;
        }
    }

    static class Target {
        @RepeatSubmit(interval = 3, unit = TimeUnit.SECONDS)
        String submit(String payload) {
            return "done:" + payload;
        }

        @RepeatSubmit(interval = 1, unit = TimeUnit.MINUTES, message = "别点那么快")
        String custom() {
            return "ok";
        }

        String unguarded() {
            return "free";
        }
    }

    private FakeGuard guard;
    private Target proxy;

    @BeforeEach
    void setUp() {
        guard = new FakeGuard();
        AspectJProxyFactory factory = new AspectJProxyFactory(new Target());
        factory.addAspect(new RepeatSubmitAspect(guard, JsonMapper.builder().build()));
        proxy = factory.getProxy();

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        request.setRemoteAddr("203.0.113.7");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    void first_submission_passes_through() {
        assertThat(proxy.submit("a")).isEqualTo("done:a");
    }

    @Test
    void identical_second_submission_is_rejected() {
        proxy.submit("a");

        assertThatThrownBy(() -> proxy.submit("a"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode().getCode())
                        .isEqualTo(CommonResultCode.REPEAT_SUBMIT.getCode()));
    }

    @Test
    void different_arguments_are_not_treated_as_repeats() {
        proxy.submit("a");

        assertThat(proxy.submit("b")).isEqualTo("done:b");
    }

    @Test
    void interval_and_unit_are_honoured() {
        proxy.submit("a");

        assertThat(guard.acquired.values()).containsExactly(Duration.ofSeconds(3));
    }

    @Test
    void custom_message_is_used_when_provided() {
        proxy.custom();

        assertThatThrownBy(proxy::custom)
                .isInstanceOf(BizException.class)
                .hasMessage("别点那么快");
    }

    @Test
    void unannotated_method_is_untouched() {
        assertThat(proxy.unguarded()).isEqualTo("free");
        assertThat(guard.acquired).isEmpty();
    }
}
