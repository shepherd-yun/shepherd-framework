package com.shepherdyun.boot.web;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "shepherd.openapi")
public class ShepherdOpenApiProperties {
    private boolean enabled = true;
    private String title = "API";
    private String version = "v1";
    private String description = "";
}
