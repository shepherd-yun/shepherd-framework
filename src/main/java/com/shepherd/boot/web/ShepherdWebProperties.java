package com.shepherd.boot.web;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * shepherd.web.* 配置。
 *
 * <p><b>注意所有 List 类型的绑定是整体替换而非追加</b>——下游一旦在 yaml 中写了某一项，
 * 必须把需要保留的默认值一并抄进去。
 */
@Data
@ConfigurationProperties(prefix = "shepherd.web")
public class ShepherdWebProperties {

    private final WrapResult wrapResult = new WrapResult();
    private final AsyncMdc asyncMdc = new AsyncMdc();
    private final Cors cors = new Cors();
    private final AccessLog accessLog = new AccessLog();

    @Data
    public static class WrapResult {
        /** controller 返回值自动包装成 Result。 */
        private boolean enabled = true;
        /** 为空表示除硬排除外全部包装；非空则只包装这些包下的 controller。 */
        private List<String> basePackages = new ArrayList<>();
    }

    @Data
    public static class AsyncMdc {
        /** 把 MDC（含 traceId）透传进 @Async 线程池。 */
        private boolean enabled = true;
    }

    @Data
    public static class Cors {
        private boolean enabled = false;
        private List<String> allowedOrigins = new ArrayList<>();
        private List<String> allowedMethods =
                new ArrayList<>(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        private List<String> allowedHeaders = new ArrayList<>(List.of("*"));
        private List<String> exposedHeaders = new ArrayList<>(List.of("X-Trace-Id"));
        private boolean allowCredentials = false;
        private Duration maxAge = Duration.ofHours(1);
    }

    @Data
    public static class AccessLog {
        private boolean enabled = false;
        private Duration slowThreshold = Duration.ofSeconds(1);
        /** 记录请求/响应体需要包装缓冲，有内存开销，默认关闭。 */
        private boolean includeBody = false;
        private int maxBodyLength = 2048;
        private List<String> sensitiveKeys = new ArrayList<>(List.of(
                "password", "oldPassword", "newPassword", "token",
                "accessToken", "refreshToken", "secret", "idCard", "phone"));
        private List<String> excludePaths = new ArrayList<>(List.of(
                "/actuator/**", "/v3/api-docs/**", "/swagger-ui/**"));
    }
}
