package com.shepherd.boot.security;

import com.shepherd.boot.core.exception.BizException;
import com.shepherd.boot.core.result.CommonResultCode;
import org.junit.jupiter.api.Test;

import java.util.List;

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
    void refresh_token_is_rejected_by_parse_access() {
        JwtTokenProvider p = provider();
        String refresh = p.generateRefreshToken(42L, "jti-1");

        assertThatThrownBy(() -> p.parseAccess(refresh))
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
