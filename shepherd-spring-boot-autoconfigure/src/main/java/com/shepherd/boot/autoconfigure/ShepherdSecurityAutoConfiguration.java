package com.shepherd.boot.autoconfigure;

import com.shepherd.boot.audit.AuditorProvider;
import com.shepherd.boot.security.*;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.DispatcherType;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.json.JsonMapper;

@AutoConfiguration(afterName = "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({SecurityFilterChain.class, Jwts.class})
@ConditionalOnProperty(prefix = "shepherd.security", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(ShepherdSecurityProperties.class)
@EnableWebSecurity
@EnableMethodSecurity
public class ShepherdSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JwtTokenProvider jwtTokenProvider(ShepherdSecurityProperties properties) {
        return new JwtTokenProvider(properties.getJwt());
    }

    @Bean
    @ConditionalOnMissingBean(LoginTokenStore.class)
    @ConditionalOnBean(StringRedisTemplate.class)
    public LoginTokenStore redisLoginTokenStore(StringRedisTemplate redis) {
        return new RedisLoginTokenStore(redis);
    }

    /**
     * 缺 Redis 时的 fail-fast。不做 no-op 降级：静默降级会让登出与踢人失效，
     * 而这在生产环境是安全问题。此 Bean 在上下文刷新时即抛出，报错可操作。
     */
    @Bean
    @ConditionalOnMissingBean(LoginTokenStore.class)
    public LoginTokenStore missingLoginTokenStore() {
        throw new IllegalStateException("""
                JWT 鉴权已启用，但找不到 LoginTokenStore（需要 StringRedisTemplate）。
                三条出路，任选其一：
                  1. 引入 spring-boot-starter-data-redis 并配置 spring.data.redis.*
                  2. 自行声明一个 LoginTokenStore Bean
                  3. 设置 shepherd.security.enabled=false 关闭 Starter 的 JWT 安全链
                     （注意这不等于关闭鉴权：只要 classpath 上仍有 spring-security，
                     Spring Boot 会回落到其默认的 basic/form 登录链并生成一个随机密码；
                     若要完全放行所有请求，需自行声明一个 SecurityFilterChain Bean）
                注意：不提供无状态降级实现，因为那会使登出与踢人静默失效。""");
    }

    @Bean
    @ConditionalOnMissingBean
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtTokenProvider tokenProvider,
                                                          LoginTokenStore tokenStore) {
        return new JwtAuthenticationFilter(tokenProvider, tokenStore);
    }

    /** 关闭 servlet 链上的自动注册，该过滤器只应由安全过滤链执行。 */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> reg = new FilterRegistrationBean<>(filter);
        reg.setEnabled(false);
        return reg;
    }

    @Bean
    @ConditionalOnMissingBean
    public RestAuthEntryPoint restAuthEntryPoint(JsonMapper jsonMapper) {
        return new RestAuthEntryPoint(jsonMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public RestAccessDeniedHandler restAccessDeniedHandler(JsonMapper jsonMapper) {
        return new RestAccessDeniedHandler(jsonMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    @ConditionalOnMissingBean(AuditorProvider.class)
    public AuditorProvider securityContextAuditorProvider() {
        return new SecurityContextAuditorProvider();
    }

    /**
     * 默认安全链。下游声明自己的 SecurityFilterChain Bean 即完全接管，
     * 上面那些零件仍可注入复用。
     */
    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain shepherdFilterChain(HttpSecurity http,
                                                   ShepherdSecurityProperties properties,
                                                   JwtAuthenticationFilter jwtAuthenticationFilter,
                                                   RestAuthEntryPoint authEntryPoint,
                                                   RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        String[] permitUrls = properties.getPermitUrls().toArray(String[]::new);
        http
                // 让 Security 拾取容器里的 CorsConfigurationSource 并放行预检 OPTIONS。
                // 没有这一行，shepherd.web.cors.* 在启用 Security 时完全不起作用。
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // ASYNC / ERROR 是同一请求的再次分发，原始 REQUEST 分发已鉴过权。
                        // JWT 过滤器（OncePerRequestFilter）不在这两种分发上重跑，无状态链也不保存
                        // SecurityContext，不放行的话 SseEmitter / Callable / DeferredResult 的结果
                        // 会在异步分发时被当成未登录拒掉，已经开始的流被掐断。
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        .requestMatchers(permitUrls).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
