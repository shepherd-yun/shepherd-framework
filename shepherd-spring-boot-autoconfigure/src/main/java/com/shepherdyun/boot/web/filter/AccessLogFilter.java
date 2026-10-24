package com.shepherdyun.boot.web.filter;

import com.shepherdyun.boot.web.ClientIps;
import com.shepherdyun.boot.web.ShepherdWebProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 访问日志：method · uri · 状态码 · 耗时 · 客户端 IP，可选带请求/响应体。
 * 超过 slow-threshold 打 WARN，否则 INFO。默认关闭（shepherd.web.access-log.enabled）。
 */
@Slf4j
public class AccessLogFilter extends OncePerRequestFilter {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final ShepherdWebProperties.AccessLog properties;
    private final SensitiveMasker masker;

    public AccessLogFilter(ShepherdWebProperties.AccessLog properties) {
        this.properties = properties;
        this.masker = new SensitiveMasker(properties.getSensitiveKeys(), properties.getMaxBodyLength());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.isEnabled()) {
            return true;
        }
        String uri = request.getRequestURI();
        return properties.getExcludePaths().stream().anyMatch(p -> PATH_MATCHER.match(p, uri));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        long start = System.nanoTime();

        if (!properties.isIncludeBody()) {
            try {
                chain.doFilter(request, response);
            } finally {
                log(request, response.getStatus(), start, null, null);
            }
            return;
        }

        // Spring Framework 7 的 ContentCachingRequestWrapper 必须显式给缓存上限。
        // 用 max-body-length：反正超出部分也不会进日志，多缓存只是白占内存。
        ContentCachingRequestWrapper wrappedRequest =
                new ContentCachingRequestWrapper(request, properties.getMaxBodyLength());
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
        try {
            chain.doFilter(wrappedRequest, wrappedResponse);
        } finally {
            String requestBody = masker.mask(readBody(wrappedRequest.getContentAsByteArray()));
            String responseBody = masker.mask(readBody(wrappedResponse.getContentAsByteArray()));
            // 必须回写：响应体被缓冲在 wrapper 里，不 copy 回去客户端会收到空 body。
            // 放在读取之后、日志之前，且无论是否异常都要执行。
            wrappedResponse.copyBodyToResponse();
            log(request, wrappedResponse.getStatus(), start, requestBody, responseBody);
        }
    }

    private static String readBody(byte[] raw) {
        return raw.length == 0 ? "" : new String(raw, StandardCharsets.UTF_8);
    }

    private void log(HttpServletRequest request, int status, long startNanos,
                     @Nullable String requestBody, @Nullable String responseBody) {
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
        boolean slow = elapsedMs >= properties.getSlowThreshold().toMillis();
        String suffix = (requestBody == null)
                ? ""
                : " req=" + requestBody + " resp=" + responseBody;

        if (slow) {
            log.warn("{} {} -> {} {}ms (slow) from {}{}",
                    request.getMethod(), request.getRequestURI(), status, elapsedMs,
                    ClientIps.resolve(request), suffix);
        } else {
            log.info("{} {} -> {} {}ms from {}{}",
                    request.getMethod(), request.getRequestURI(), status, elapsedMs,
                    ClientIps.resolve(request), suffix);
        }
    }
}
