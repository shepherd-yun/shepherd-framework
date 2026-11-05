package com.shepherdyun.boot.autoconfigure;

import com.shepherdyun.boot.audit.AuditorProvider;
import com.shepherdyun.boot.web.ShepherdWebProperties;
import com.shepherdyun.boot.web.advice.GlobalExceptionHandler;
import com.shepherdyun.boot.web.advice.ResultBodyAdvice;
import com.shepherdyun.boot.web.advice.SecurityExceptionHandler;
import com.shepherdyun.boot.web.advice.ValidationExceptionHandler;
import com.shepherdyun.boot.web.async.MdcTaskDecorator;
import com.shepherdyun.boot.web.cors.ShepherdCorsConfigurationSource;
import com.shepherdyun.boot.web.filter.AccessLogFilter;
import com.shepherdyun.boot.web.filter.TraceIdFilter;
import jakarta.validation.ConstraintViolationException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.task.TaskDecorator;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.util.Optional;

@AutoConfiguration(after = ShepherdSecurityAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(ShepherdWebProperties.class)
public class ShepherdWebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "shepherd.web.wrap-result", name = "enabled",
                           havingValue = "true", matchIfMissing = true)
    public ResultBodyAdvice resultBodyAdvice(ShepherdWebProperties properties, JsonMapper jsonMapper) {
        return new ResultBodyAdvice(properties.getWrapResult(), jsonMapper);
    }

    /**
     * 必须 @ConditionalOnMissingBean(TaskDecorator.class)：Boot 用 ObjectProvider 取唯一
     * TaskDecorator，上下文里存在两个时会静默跳过、透传失效且不报错。下游自定义时让位。
     */
    @Bean
    @ConditionalOnMissingBean(TaskDecorator.class)
    @ConditionalOnProperty(prefix = "shepherd.web.async-mdc", name = "enabled",
                           havingValue = "true", matchIfMissing = true)
    public MdcTaskDecorator mdcTaskDecorator() {
        return new MdcTaskDecorator();
    }

    /**
     * 默认关闭：前端 dev 走 vite proxy 同源、生产多为同域部署。CORS 必须显式开启。
     * <p>
     * Bean 名必须是 corsConfigurationSource：Spring Security 的 {@code .cors()} 只按这个名字查找。
     * 也只能按名字让位，不能按类型：Spring MVC 的 mvcHandlerMappingIntrospector 同样实现
     * CorsConfigurationSource，按类型判断时这个 Bean 永远建不出来。
     */
    @Bean
    @ConditionalOnMissingBean(name = "corsConfigurationSource")
    @ConditionalOnProperty(prefix = "shepherd.web.cors", name = "enabled", havingValue = "true")
    public CorsConfigurationSource corsConfigurationSource(ShepherdWebProperties properties) {
        return ShepherdCorsConfigurationSource.from(properties.getCors());
    }

    /**
     * 没有安全链时（只用 starter-web）没人消费上面的 Bean，由这个过滤器生效；排在 TraceIdFilter 之后。
     * 有安全链时由它的 {@code .cors()} 负责，不重复注册。类型写字符串：security 不在 classpath 时也能解析。
     */
    @Bean
    @ConditionalOnMissingBean(type = "org.springframework.security.web.SecurityFilterChain",
                              name = "corsFilterRegistration")
    @ConditionalOnProperty(prefix = "shepherd.web.cors", name = "enabled", havingValue = "true")
    public FilterRegistrationBean<CorsFilter> corsFilterRegistration(
            @Qualifier("corsConfigurationSource") CorsConfigurationSource source) {
        FilterRegistrationBean<CorsFilter> reg = new FilterRegistrationBean<>(new CorsFilter(source));
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return reg;
    }

    @Bean
    @ConditionalOnMissingBean(name = "traceIdFilterRegistration")
    public FilterRegistrationBean<TraceIdFilter> traceIdFilterRegistration() {
        FilterRegistrationBean<TraceIdFilter> reg = new FilterRegistrationBean<>(new TraceIdFilter());
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return reg;
    }

    @Bean
    @ConditionalOnMissingBean(name = "accessLogFilterRegistration")
    @ConditionalOnProperty(prefix = "shepherd.web.access-log", name = "enabled", havingValue = "true")
    public FilterRegistrationBean<AccessLogFilter> accessLogFilterRegistration(
            ShepherdWebProperties properties) {
        FilterRegistrationBean<AccessLogFilter> reg =
                new FilterRegistrationBean<>(new AccessLogFilter(properties.getAccessLog()));
        // 紧随 TraceIdFilter 之后，保证日志里带得上 traceId
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return reg;
    }

    /** Long/long 序列化为字符串，避免 JS 精度丢失。 */
    @Bean
    @ConditionalOnMissingBean(name = "shepherdLongToStringCustomizer")
    public JsonMapperBuilderCustomizer shepherdLongToStringCustomizer() {
        return builder -> {
            SimpleModule module = new SimpleModule();
            module.addSerializer(Long.class, ToStringSerializer.instance);
            module.addSerializer(Long.TYPE, ToStringSerializer.instance);
            builder.addModules(module);
        };
    }

    /** 无 Spring Security 时的兜底实现：不填充 createBy/updateBy。 */
    @Bean
    @ConditionalOnMissingBean(AuditorProvider.class)
    public AuditorProvider emptyAuditorProvider() {
        return Optional::empty;
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(AccessDeniedException.class)
    static class SecurityAwareAdviceConfiguration {

        @Bean
        @ConditionalOnMissingBean
        SecurityExceptionHandler securityExceptionHandler() {
            return new SecurityExceptionHandler();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(ConstraintViolationException.class)
    static class ValidationAwareAdviceConfiguration {

        @Bean
        @ConditionalOnMissingBean
        ValidationExceptionHandler validationExceptionHandler() {
            return new ValidationExceptionHandler();
        }
    }
}
