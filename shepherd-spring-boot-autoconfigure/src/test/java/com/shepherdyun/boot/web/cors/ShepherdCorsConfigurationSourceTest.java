package com.shepherdyun.boot.web.cors;

import com.shepherdyun.boot.web.ShepherdWebProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShepherdCorsConfigurationSourceTest {

    private static ShepherdWebProperties.Cors cors(List<String> origins, boolean credentials) {
        ShepherdWebProperties.Cors c = new ShepherdWebProperties.Cors();
        c.setEnabled(true);
        c.setAllowedOrigins(origins);
        c.setAllowCredentials(credentials);
        c.setMaxAge(Duration.ofMinutes(30));
        return c;
    }

    @Test
    void properties_are_mapped_onto_cors_configuration() {
        CorsConfigurationSource source =
                ShepherdCorsConfigurationSource.from(cors(List.of("https://app.example.com"), false));

        HttpServletRequest request = new MockHttpServletRequest("GET", "/api/anything");
        CorsConfiguration config = source.getCorsConfiguration(request);

        assertThat(config).isNotNull();
        assertThat(config.getAllowedOrigins()).containsExactly("https://app.example.com");
        assertThat(config.getAllowedMethods()).contains("GET", "POST", "OPTIONS");
        assertThat(config.getExposedHeaders()).contains("X-Trace-Id");
        assertThat(config.getMaxAge()).isEqualTo(1800L);
    }

    /**
     * 浏览器规范禁止 allow-credentials + 通配 origin 的组合。让它启动就失败，
     * 好过上线后在浏览器里得到一个难排查的 CORS 报错。
     */
    @Test
    void wildcard_origin_with_credentials_fails_fast() {
        assertThatThrownBy(() -> ShepherdCorsConfigurationSource.from(cors(List.of("*"), true)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("allow-credentials");
    }

    @Test
    void wildcard_origin_without_credentials_is_allowed() {
        CorsConfigurationSource source = ShepherdCorsConfigurationSource.from(cors(List.of("*"), false));

        CorsConfiguration config =
                source.getCorsConfiguration(new MockHttpServletRequest("GET", "/api/anything"));

        assertThat(config).isNotNull();
        assertThat(config.getAllowedOrigins()).containsExactly("*");
    }
}
