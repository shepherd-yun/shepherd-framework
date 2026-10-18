package com.shepherd.boot.autoconfigure;

import com.shepherd.boot.security.JwtTokenProvider;
import com.shepherd.boot.security.LoginTokenStore;
import com.shepherd.boot.security.LoginUser;
import com.shepherd.boot.web.code.CommonResultCode;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.concurrent.Callable;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

class ShepherdSecurityAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ShepherdSecurityAutoConfiguration.class))
            .withUserConfiguration(TestConfig.class)
            .withPropertyValues("shepherd.security.jwt.secret=test-secret-key-that-is-at-least-32-bytes-long!!");

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    static class TestConfig {
        @Bean JsonMapper jsonMapper() { return JsonMapper.builder().build(); }
        @Bean LoginTokenStore loginTokenStore() { return new AlwaysValidTokenStore(); }
        @Bean ProbeController probeController() { return new ProbeController(); }
    }

    static class AlwaysValidTokenStore implements LoginTokenStore {
        @Override public void save(Long userId, String jti, long ttlSeconds) { }
        @Override public boolean isValid(Long userId, String jti) { return true; }
        @Override public void revoke(Long userId, String jti) { }
    }

    @RestController
    static class ProbeController {
        @GetMapping("/sync")  String sync()            { return "ok"; }
        @GetMapping("/async") Callable<String> async() { return () -> "streamed"; }
    }

    private static MockMvc mvc(WebApplicationContext context) {
        return MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
                .build();
    }

    private static String bearer(WebApplicationContext context) {
        LoginUser user = new LoginUser(1L, "alice", List.of("USER"), "jti-1");
        return "Bearer " + context.getBean(JwtTokenProvider.class).generateAccessToken(user);
    }

    /** 未登录按框架约定返回 HTTP 200 + Result.code=UNAUTHORIZED，见 RestAuthEntryPoint。 */
    @Test
    void request_without_token_is_rejected() {
        runner.run(context -> mvc(context).perform(get("/sync"))
                .andExpect(jsonPath("$.code").value(CommonResultCode.UNAUTHORIZED.getCode())));
    }

    @Test
    void authenticated_sync_request_passes() {
        runner.run(context -> mvc(context)
                .perform(get("/sync").header("Authorization", bearer(context)))
                .andExpect(content().string("ok")));
    }

    /**
     * Callable / DeferredResult / SseEmitter 的结果在 ASYNC 分发里写出。JWT 过滤器（OncePerRequestFilter）
     * 默认不在异步分发上再跑一遍，无状态链又不保存 SecurityContext，若链对 ASYNC 分发也要求认证，
     * 原请求已鉴过权的流会在这里被当成未登录掐断。
     */
    @Test
    void async_dispatch_of_authenticated_request_is_not_rejected() {
        runner.run(context -> {
            MockMvc mvc = mvc(context);
            MvcResult started = mvc.perform(get("/async").header("Authorization", bearer(context)))
                    .andExpect(request().asyncStarted())
                    .andReturn();

            mvc.perform(asyncDispatch(started))
                    .andExpect(content().string("streamed"));
        });
    }
}
