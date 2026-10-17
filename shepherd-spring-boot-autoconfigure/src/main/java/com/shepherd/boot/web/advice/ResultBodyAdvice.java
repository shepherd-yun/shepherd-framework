package com.shepherd.boot.web.advice;

import com.shepherd.boot.web.ShepherdWebProperties;
import com.shepherd.boot.web.vo.Result;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * 把 controller 返回值自动包装成 {@link Result}，让业务代码不必手写 Result.success(...)。
 *
 * <p>放行规则见 {@link #supports}。逃生口是 {@link NoResultWrap}。
 */
@RestControllerAdvice
public class ResultBodyAdvice implements ResponseBodyAdvice<Object> {

    /**
     * 硬排除，不可通过配置打开：这些包的 controller 有自己的响应契约，
     * 包装后 Swagger UI 与 actuator 端点都会失效。
     *
     * <p>Boot 4 把 actuator 的 MVC 端点实现挪到了 {@code org.springframework.boot.webmvc.actuate}，
     * 只写旧包名会让 /actuator/health 被包装。旧包名留着不删：多排除一个包没有代价，漏排除一个就会把端点包坏。
     */
    private static final List<String> HARD_EXCLUDED_PACKAGES = List.of(
            "org.springdoc.",
            "org.springframework.boot.actuate.",
            "org.springframework.boot.webmvc.actuate.");

    private final ShepherdWebProperties.WrapResult properties;
    private final JsonMapper jsonMapper;

    public ResultBodyAdvice(ShepherdWebProperties.WrapResult properties, JsonMapper jsonMapper) {
        this.properties = properties;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public boolean supports(MethodParameter returnType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        Class<?> declaringClass = returnType.getContainingClass();
        String className = declaringClass.getName();

        for (String excluded : HARD_EXCLUDED_PACKAGES) {
            if (className.startsWith(excluded)) {
                return false;
            }
        }

        List<String> basePackages = properties.getBasePackages();
        if (!basePackages.isEmpty() && basePackages.stream().noneMatch(className::startsWith)) {
            return false;
        }

        if (AnnotatedElementUtils.hasAnnotation(declaringClass, NoResultWrap.class)
                || returnType.hasMethodAnnotation(NoResultWrap.class)) {
            return false;
        }

        Class<?> type = returnType.getParameterType();
        if (Result.class.isAssignableFrom(type)
                || byte[].class.isAssignableFrom(type)
                || Resource.class.isAssignableFrom(type)
                || StreamingResponseBody.class.isAssignableFrom(type)) {
            return false;
        }
        return true;
    }

    @Override
    public @Nullable Object beforeBodyWrite(@Nullable Object body,
                                            MethodParameter returnType,
                                            MediaType selectedContentType,
                                            Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                            ServerHttpRequest request,
                                            ServerHttpResponse response) {
        // ResponseEntity<Result<T>> 的 body 到这里已被拆出来，而 supports() 看到的静态类型
        // 是 ResponseEntity 而非 Result，所以必须在运行时再兜一次，否则会二次包装。
        if (body instanceof Result<?>) {
            return body;
        }
        // byte[] / Resource 经 ResponseEntity 返回时同样躲过了 supports() 的静态判断。
        if (body instanceof byte[] || body instanceof Resource) {
            return body;
        }

        Result<?> wrapped = (body == null) ? Result.success() : Result.success(body);

        // String 走 StringHttpMessageConverter，直接返回 Result 对象会 ClassCastException：
        // 这里手工序列化成 JSON 文本，并把 Content-Type 从 text/plain 改回 application/json。
        if (StringHttpMessageConverter.class.isAssignableFrom(selectedConverterType)) {
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            return jsonMapper.writeValueAsString(wrapped);
        }
        return wrapped;
    }
}
