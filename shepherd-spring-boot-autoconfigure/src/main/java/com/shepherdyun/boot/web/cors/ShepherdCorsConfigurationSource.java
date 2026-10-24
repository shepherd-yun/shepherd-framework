package com.shepherdyun.boot.web.cors;

import com.shepherdyun.boot.web.ShepherdWebProperties;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/** 由 shepherd.web.cors.* 构造 CorsConfigurationSource。 */
public final class ShepherdCorsConfigurationSource {

    private ShepherdCorsConfigurationSource() {
    }

    public static CorsConfigurationSource from(ShepherdWebProperties.Cors properties) {
        if (properties.isAllowCredentials() && properties.getAllowedOrigins().contains("*")) {
            throw new IllegalStateException("""
                    shepherd.web.cors 配置非法：allow-credentials=true 时 allowed-origins 不能含 "*"。
                    这是浏览器规范禁止的组合（带凭证的请求不接受通配 origin），运行期只会得到
                    一个难以排查的 CORS 失败。请把 allowed-origins 写成具体域名列表，
                    或将 allow-credentials 置为 false。""");
        }

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.getAllowedOrigins());
        config.setAllowedMethods(properties.getAllowedMethods());
        config.setAllowedHeaders(properties.getAllowedHeaders());
        config.setExposedHeaders(properties.getExposedHeaders());
        config.setAllowCredentials(properties.isAllowCredentials());
        config.setMaxAge(properties.getMaxAge().toSeconds());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
