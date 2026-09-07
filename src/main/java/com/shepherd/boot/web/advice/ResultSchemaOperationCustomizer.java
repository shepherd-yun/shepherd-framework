package com.shepherd.boot.web.advice;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.jspecify.annotations.Nullable;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;

/**
 * 自动包装让 springdoc 生成的响应 schema 失真（显示 UserVO 而非 Result&lt;UserVO&gt;），
 * 这里把 2xx 响应的 schema 补回一层 Result 信封。
 *
 * <p>标了 {@link NoResultWrap} 的方法/类不包装，与 {@link ResultBodyAdvice} 的放行规则保持一致。
 */
public class ResultSchemaOperationCustomizer implements OperationCustomizer {

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        if (handlerMethod.hasMethodAnnotation(NoResultWrap.class)
                || AnnotatedElementUtils.hasAnnotation(handlerMethod.getBeanType(), NoResultWrap.class)) {
            return operation;
        }
        @Nullable ApiResponses responses = operation.getResponses();
        if (responses == null) {
            return operation;
        }
        responses.forEach((status, response) -> {
            if (status.startsWith("2")) {
                wrapContent(response);
            }
        });
        return operation;
    }

    private static void wrapContent(ApiResponse response) {
        @Nullable Content content = response.getContent();
        if (content == null) {
            return;
        }
        content.forEach((mediaTypeName, mediaType) -> {
            @Nullable Schema<?> original = mediaType.getSchema();
            if (original != null) {
                mediaType.setSchema(envelope(original));
            }
        });
    }

    private static Schema<?> envelope(Schema<?> data) {
        ObjectSchema wrapper = new ObjectSchema();
        wrapper.addProperty("code", new IntegerSchema());
        wrapper.addProperty("message", new StringSchema());
        wrapper.addProperty("data", data);
        wrapper.addProperty("traceId", new StringSchema());
        return wrapper;
    }
}
