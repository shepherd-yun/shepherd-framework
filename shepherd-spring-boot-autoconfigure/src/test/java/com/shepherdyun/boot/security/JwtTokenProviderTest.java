package com.shepherdyun.boot.security;

import com.shepherdyun.boot.web.exception.BizException;
import com.shepherdyun.boot.web.code.CommonResultCode;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private ShepherdSecurityProperties.Jwt jwtProps(String secret) {
        ShepherdSecurityProperties.Jwt props = new ShepherdSecurityProperties.Jwt();
        props.setSecret(secret);
        props.setAccessTtlSeconds(3600);
        props.setRefreshTtlSeconds(7200);
        return props;
    }

    private JwtTokenProvider provider() {
        return new JwtTokenProvider(jwtProps("test-secret-key-that-is-at-least-32-bytes-long!!"));
    }

    @Test
    void access_token_round_trips_login_user() {
        JwtTokenProvider p = provider();
        LoginUser user = new LoginUser(42L, "alice", List.of("ADMIN"), "jti-1");

        LoginUser parsed = p.parseAccess(p.generateAccessToken(user));

        assertThat(parsed.userId()).isEqualTo(42L);
        assertThat(parsed.username()).isEqualTo("alice");
        assertThat(parsed.roles()).containsExactly("ADMIN");
        assertThat(parsed.jti()).isEqualTo("jti-1");
    }

    @Test
    void attributes_round_trip_through_access_token() {
        JwtTokenProvider p = provider();
        LoginUser user = new LoginUser(42L, "alice", List.of("FORWARDER"), "jti-1",
                Map.of("userType", "FORWARDER", "orgId", 7L, "divisionIds", List.of(1L, 2L)));

        LoginUser parsed = p.parseAccess(p.generateAccessToken(user));

        assertThat(parsed.attribute("userType")).isEqualTo("FORWARDER");
        assertThat(parsed.attributeLong("orgId")).isEqualTo(7L);
        assertThat(parsed.attribute("divisionIds")).asInstanceOf(InstanceOfAssertFactories.LIST)
                .extracting(n -> ((Number) n).longValue())
                .containsExactly(1L, 2L);
    }

    /**
     * 钉死 JSON 往返会丢失 Java 数值类型这件事：写进去的 Long 回来是 Integer。
     * {@link LoginUser#attributeLong} 就是为吸收这个差异而存在的，下游别直接强转 Long。
     */
    @Test
    void small_long_attribute_comes_back_as_integer_after_json_round_trip() {
        JwtTokenProvider p = provider();
        LoginUser user = new LoginUser(42L, "alice", List.of(), "jti-1", Map.of("orgId", 7L));

        LoginUser parsed = p.parseAccess(p.generateAccessToken(user));

        assertThat(parsed.attribute("orgId")).isInstanceOf(Integer.class);
        assertThat(parsed.attributeLong("orgId")).isEqualTo(7L);
    }

    @Test
    void attributes_live_under_one_claim_so_they_cannot_shadow_security_claims() {
        JwtTokenProvider p = provider();
        LoginUser user = new LoginUser(42L, "alice", List.of("USER"), "jti-1",
                Map.of("type", "refresh", "sub", "999", "roles", List.of("ADMIN")));

        LoginUser parsed = p.parseAccess(p.generateAccessToken(user));

        assertThat(parsed.userId()).isEqualTo(42L);
        assertThat(parsed.roles()).containsExactly("USER");
        assertThat(parsed.attribute("roles")).isEqualTo(List.of("ADMIN"));
    }

    @Test
    void token_without_attributes_parses_to_empty_map() {
        JwtTokenProvider p = provider();

        LoginUser parsed = p.parseAccess(p.generateAccessToken(new LoginUser(42L, "alice", List.of(), "jti-1")));

        assertThat(parsed.attributes()).isEmpty();
        assertThat(parsed.attribute("orgId")).isNull();
        assertThat(parsed.attributeLong("orgId")).isNull();
    }

    @Test
    void refresh_token_is_rejected_by_parse_access() {
        JwtTokenProvider p = provider();
        String refresh = p.generateRefreshToken(42L, "jti-1");

        assertThatThrownBy(() -> p.parseAccess(refresh))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(CommonResultCode.TOKEN_INVALID);
    }

    @Test
    void refresh_token_round_trips_user_id_and_jti() {
        JwtTokenProvider p = provider();

        RefreshClaims parsed = p.parseRefresh(p.generateRefreshToken(42L, "jti-1"));

        assertThat(parsed.userId()).isEqualTo(42L);
        assertThat(parsed.jti()).isEqualTo("jti-1");
    }

    /** access token 寿命短、会出现在每个请求头里，泄露面大；拿它换新 token 等于把短期凭证变成长期凭证。 */
    @Test
    void access_token_is_rejected_by_parse_refresh() {
        JwtTokenProvider p = provider();
        String access = p.generateAccessToken(new LoginUser(42L, "alice", List.of(), "jti-1"));

        assertThatThrownBy(() -> p.parseRefresh(access))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(CommonResultCode.TOKEN_INVALID);
    }

    @Test
    void expired_refresh_token_is_reported_as_expired() {
        ShepherdSecurityProperties.Jwt props = jwtProps("test-secret-key-that-is-at-least-32-bytes-long!!");
        props.setRefreshTtlSeconds(-1);
        JwtTokenProvider p = new JwtTokenProvider(props);
        String refresh = p.generateRefreshToken(42L, "jti-1");

        assertThatThrownBy(() -> p.parseRefresh(refresh))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(CommonResultCode.TOKEN_EXPIRED);
    }

    @Test
    void refresh_token_signed_with_another_secret_is_invalid() {
        JwtTokenProvider other = new JwtTokenProvider(jwtProps("another-secret-key-that-is-at-least-32-bytes!!"));
        String forged = other.generateRefreshToken(42L, "jti-1");

        assertThatThrownBy(() -> provider().parseRefresh(forged))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(CommonResultCode.TOKEN_INVALID);
    }

    @Test
    void missing_secret_fails_fast_with_actionable_message() {
        assertThatThrownBy(() -> new JwtTokenProvider(jwtProps(null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("shepherd.security.jwt.secret");
    }

    @Test
    void short_secret_fails_fast() {
        assertThatThrownBy(() -> new JwtTokenProvider(jwtProps("too-short")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32");
    }
}
