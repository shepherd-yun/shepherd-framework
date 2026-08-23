package com.shepherd.boot.security;

import lombok.Data;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "shepherd.security")
public class ShepherdSecurityProperties {

    /** 关闭后不注册任何安全 Bean，也不再要求 Redis。 */
    private boolean enabled = true;

    /**
     * 免认证路径。注意：List 绑定是**整体替换**而非追加——
     * 下游一旦在 yaml 中写了 permit-urls，就必须把需要的默认项一并列出。
     */
    private List<String> permitUrls = new ArrayList<>(List.of(
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/actuator/health"
    ));

    private final Jwt jwt = new Jwt();

    @Data
    public static class Jwt {
        /** 无默认值：Starter 不向外分发弱密钥。缺失或短于 32 字节则启动失败。 */
        private @Nullable String secret;
        private long accessTtlSeconds = 7200;
        private long refreshTtlSeconds = 1209600;
    }
}
