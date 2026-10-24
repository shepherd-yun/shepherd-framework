package com.shepherdyun.boot.autoconfigure;

import com.shepherdyun.boot.web.ShepherdOpenApiProperties;
import com.shepherdyun.boot.web.advice.ResultSchemaOperationCustomizer;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** 省掉每个下游项目重复抄一份 SwaggerConfig。下游自己声明 OpenAPI Bean 即完全接管。 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(OpenAPI.class)
@ConditionalOnProperty(prefix = "shepherd.openapi", name = "enabled",
                       havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(ShepherdOpenApiProperties.class)
public class ShepherdOpenApiAutoConfiguration {

    private static final String BEARER = "bearer";

    @Bean
    @ConditionalOnMissingBean(OpenAPI.class)
    public OpenAPI shepherdOpenApi(ShepherdOpenApiProperties properties) {
        return new OpenAPI()
                .info(new Info()
                        .title(properties.getTitle())
                        .version(properties.getVersion())
                        .description(properties.getDescription()))
                .components(new Components().addSecuritySchemes(BEARER,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme(BEARER)
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "shepherd.web.wrap-result", name = "enabled",
                           havingValue = "true", matchIfMissing = true)
    public ResultSchemaOperationCustomizer resultSchemaOperationCustomizer() {
        return new ResultSchemaOperationCustomizer();
    }
}
