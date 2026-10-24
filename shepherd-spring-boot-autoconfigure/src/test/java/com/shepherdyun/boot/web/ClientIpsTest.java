package com.shepherdyun.boot.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpsTest {

    @Test
    void x_forwarded_for_first_hop_wins() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.7, 70.41.3.18, 150.172.238.178");
        request.setRemoteAddr("10.0.0.1");

        assertThat(ClientIps.resolve(request)).isEqualTo("203.0.113.7");
    }

    @Test
    void x_real_ip_is_used_when_forwarded_for_absent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Real-IP", "203.0.113.9");
        request.setRemoteAddr("10.0.0.1");

        assertThat(ClientIps.resolve(request)).isEqualTo("203.0.113.9");
    }

    @Test
    void remote_addr_is_the_last_resort() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");

        assertThat(ClientIps.resolve(request)).isEqualTo("10.0.0.1");
    }

    @Test
    void blank_and_unknown_headers_are_skipped() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "   ");
        request.addHeader("X-Real-IP", "unknown");
        request.setRemoteAddr("10.0.0.1");

        assertThat(ClientIps.resolve(request)).isEqualTo("10.0.0.1");
    }

    @Test
    void never_returns_null_even_without_remote_addr() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(null);

        assertThat(ClientIps.resolve(request)).isEqualTo("unknown");
    }
}
