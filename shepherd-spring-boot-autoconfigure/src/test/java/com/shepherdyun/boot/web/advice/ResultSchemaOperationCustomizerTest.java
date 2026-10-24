package com.shepherdyun.boot.web.advice;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;

import static org.assertj.core.api.Assertions.assertThat;

class ResultSchemaOperationCustomizerTest {

    @RestController
    static class Plain {
        public String get() { return "x"; }
    }

    @NoResultWrap
    @RestController
    static class Raw {
        public String get() { return "x"; }
    }

    private final ResultSchemaOperationCustomizer customizer = new ResultSchemaOperationCustomizer();

    private static Operation operationWithSchema(String status) {
        MediaType mediaType = new MediaType().schema(new StringSchema());
        ApiResponse response = new ApiResponse()
                .content(new Content().addMediaType("application/json", mediaType));
        return new Operation().responses(new ApiResponses().addApiResponse(status, response));
    }

    private static HandlerMethod handler(Object bean) throws Exception {
        return new HandlerMethod(bean, bean.getClass().getMethod("get"));
    }

    @Test
    void success_response_schema_is_wrapped_in_the_result_envelope() throws Exception {
        Operation operation = operationWithSchema("200");

        customizer.customize(operation, handler(new Plain()));

        Schema<?> schema = operation.getResponses().get("200")
                .getContent().get("application/json").getSchema();
        assertThat(schema.getProperties()).containsKeys("code", "message", "data", "traceId");
        assertThat(schema.getProperties().get("data")).isInstanceOf(StringSchema.class);
    }

    @Test
    void non_2xx_responses_are_left_alone() throws Exception {
        Operation operation = operationWithSchema("404");

        customizer.customize(operation, handler(new Plain()));

        Schema<?> schema = operation.getResponses().get("404")
                .getContent().get("application/json").getSchema();
        assertThat(schema).isInstanceOf(StringSchema.class);
    }

    @Test
    void no_result_wrap_controllers_are_left_alone() throws Exception {
        Operation operation = operationWithSchema("200");

        customizer.customize(operation, handler(new Raw()));

        Schema<?> schema = operation.getResponses().get("200")
                .getContent().get("application/json").getSchema();
        assertThat(schema).isInstanceOf(StringSchema.class);
    }
}
