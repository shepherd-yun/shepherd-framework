package com.shepherd.boot.autoconfigure;

import com.shepherd.boot.audit.AuditorProvider;
import com.shepherd.boot.web.ShepherdWebProperties;
import com.shepherd.boot.web.advice.ResultBodyAdvice;
import com.shepherd.boot.web.async.MdcTaskDecorator;
import com.shepherd.boot.web.exception.GlobalExceptionHandler;
import com.shepherd.boot.web.exception.SecurityExceptionHandler;
import com.shepherd.boot.web.exception.ValidationExceptionHandler;
import com.shepherd.boot.web.filter.TraceIdFilter;
import jakarta.validation.ConstraintViolationException;
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

    @Bean
    @ConditionalOnMissingBean(name = "traceIdFilterRegistration")
    public FilterRegistrationBean<TraceIdFilter> traceIdFilterRegistration() {
        FilterRegistrationBean<TraceIdFilter> reg = new FilterRegistrationBean<>(new TraceIdFilter());
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE);
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
