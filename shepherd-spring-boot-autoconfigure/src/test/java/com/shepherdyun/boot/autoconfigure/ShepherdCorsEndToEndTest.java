package com.shepherdyun.boot.autoconfigure;

import com.shepherdyun.boot.security.LoginTokenStore;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 请求真正走过滤器链，验证 shepherd.web.cors.* 生效。
 * 上下文必须带 Spring MVC：它的 mvcHandlerMappingIntrospector 也实现 CorsConfigurationSource，
 * 不带就测不出「按类型判断 Bean 是否缺失」这类错误。
 */
class ShepherdCorsEndToEndTest {

    private static final String ALLOWED = "https://app.example.com";
    private static final String FOREIGN = "https://evil.example";

    private final WebApplicationContextRunner secured = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    JacksonAutoConfiguration.class,
                    ShepherdSecurityAutoConfiguration.class, ShepherdWebAutoConfiguration.class))
            .withUserConfiguration(MvcConfig.class, TokenStoreConfig.class)
            .withPropertyValues("shepherd.security.jwt.secret=test-secret-key-that-is-at-least-32-bytes-long!!");

    private final WebApplicationContextRunner webOnly = new WebApplicationContextRunner()
            .withClassLoader(new FilteredClassLoader(SecurityFilterChain.class))
            .withConfiguration(AutoConfigurations.of(
                    JacksonAutoConfiguration.class, ShepherdWebAutoConfiguration.class))
            .withUserConfiguration(MvcConfig.class);

    private static final String[] ENABLED = {
            "shepherd.web.cors.enabled=true",
            "shepherd.web.cors.allowed-origins=" + ALLOWED
    };

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    static class MvcConfig {
        @Bean ProbeController probeController() { return new ProbeController(); }
    }

    @Configuration(proxyBeanMethods = false)
    static class TokenStoreConfig {
        @Bean LoginTokenStore loginTokenStore() {
            return new LoginTokenStore() {
                @Override public void save(Long userId, String jti, long ttlSeconds) { }
                @Override public boolean isValid(Long userId, String jti) { return true; }
                @Override public void revoke(Long userId, String jti) { }
            };
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomSourceConfig {
        @Bean CorsConfigurationSource corsConfigurationSource() {
            CorsConfiguration config = new CorsConfiguration();
            config.addAllowedOrigin("https://custom.example.com");
            config.addAllowedMethod("*");
            UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
            source.registerCorsConfiguration("/**", config);
            return source;
        }
    }

    @RestController
    static class ProbeController {
        @GetMapping("/sync") String sync() { return "ok"; }
    }

    private static MockMvc securedMvc(WebApplicationContext context) {
        return MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
                .build();
    }

    @SuppressWarnings("unchecked")
    private static MockMvc webOnlyMvc(WebApplicationContext context) {
        FilterRegistrationBean<? extends Filter> registration =
                context.getBean("corsFilterRegistration", FilterRegistrationBean.class);
        return MockMvcBuilders.webAppContextSetup(context).addFilters(registration.getFilter()).build();
    }

    private static ResultActions preflight(MockMvc mvc, String origin) throws Exception {
        return mvc.perform(options("/sync")
                .header(HttpHeaders.ORIGIN, origin)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"));
    }

    @Test
    void with_security_allowed_origin_preflight_succeeds() {
        secured.withPropertyValues(ENABLED).run(ctx -> preflight(securedMvc(ctx), ALLOWED)
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED)));
    }

    @Test
    void with_security_foreign_origin_is_rejected() {
        secured.withPropertyValues(ENABLED).run(ctx -> {
            MockMvc mvc = securedMvc(ctx);
            preflight(mvc, FOREIGN).andExpect(status().isForbidden());
            mvc.perform(get("/sync").header(HttpHeaders.ORIGIN, FOREIGN)).andExpect(status().isForbidden());
        });
    }

    @Test
    void with_security_comma_separated_origins_are_bound_as_a_list() {
        secured.withPropertyValues("shepherd.web.cors.enabled=true",
                        "shepherd.web.cors.allowed-origins=https://a.example.com," + ALLOWED)
                .run(ctx -> preflight(securedMvc(ctx), ALLOWED).andExpect(status().isOk()));
    }

    @Test
    void with_security_custom_source_named_corsConfigurationSource_wins() {
        secured.withUserConfiguration(CustomSourceConfig.class).withPropertyValues(ENABLED).run(ctx -> {
            MockMvc mvc = securedMvc(ctx);
            preflight(mvc, "https://custom.example.com").andExpect(status().isOk());
            preflight(mvc, ALLOWED).andExpect(status().isForbidden());
        });
    }

    @Test
    void with_security_no_extra_servlet_filter_is_registered() {
        secured.withPropertyValues(ENABLED)
                .run(ctx -> assertThat(ctx.containsBean("corsFilterRegistration")).isFalse());
    }

    @Test
    void with_security_cors_stays_off_by_default() {
        secured.run(ctx -> {
            assertThat(ctx.containsBean("corsConfigurationSource")).isFalse();
            assertThat(ctx.containsBean("corsFilterRegistration")).isFalse();
        });
    }

    @Test
    void without_security_allowed_origin_preflight_succeeds() {
        webOnly.withPropertyValues(ENABLED).run(ctx -> preflight(webOnlyMvc(ctx), ALLOWED)
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED)));
    }

    @Test
    void without_security_foreign_origin_is_rejected() {
        webOnly.withPropertyValues(ENABLED).run(ctx -> preflight(webOnlyMvc(ctx), FOREIGN)
                .andExpect(status().isForbidden()));
    }

    @Test
    void without_security_cors_stays_off_by_default() {
        webOnly.run(ctx -> assertThat(ctx.containsBean("corsFilterRegistration")).isFalse());
    }
}
