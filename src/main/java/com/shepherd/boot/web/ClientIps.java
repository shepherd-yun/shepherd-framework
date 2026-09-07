package com.shepherd.boot.web;

import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * 客户端 IP 解析。
 *
 * <p><b>安全提示</b>：X-Forwarded-For / X-Real-IP 都是客户端可伪造的请求头。
 * 在没有可信反向代理覆写它们的部署里，基于 IP 的限流只是「防误触」量级的措施，
 * 不具备对抗恶意流量的安全强度。README 需同样注明。
 */
public final class ClientIps {

    private static final List<String> HEADERS = List.of("X-Forwarded-For", "X-Real-IP");
    private static final String UNKNOWN = "unknown";

    private ClientIps() {
    }

    public static String resolve(HttpServletRequest request) {
        for (String header : HEADERS) {
            String value = request.getHeader(header);
            if (isUsable(value)) {
                // X-Forwarded-For 是 "client, proxy1, proxy2"，首段才是原始客户端
                int comma = value.indexOf(',');
                String first = (comma >= 0 ? value.substring(0, comma) : value).trim();
                if (isUsable(first)) {
                    return first;
                }
            }
        }
        String remote = request.getRemoteAddr();
        return isUsable(remote) ? remote : UNKNOWN;
    }

    private static boolean isUsable(@Nullable String value) {
        return value != null && !value.isBlank() && !UNKNOWN.equalsIgnoreCase(value.trim());
    }
}
