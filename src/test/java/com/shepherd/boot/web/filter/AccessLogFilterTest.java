package com.shepherd.boot.web.filter;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.shepherd.boot.web.ShepherdWebProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AccessLogFilterTest {

    private ListAppender<ILoggingEvent> appender;
    private Logger logger;

    @BeforeEach
    void attachAppender() {
        logger = (Logger) LoggerFactory.getLogger(AccessLogFilter.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.DEBUG);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
    }

    private static ShepherdWebProperties.AccessLog props() {
        ShepherdWebProperties.AccessLog p = new ShepherdWebProperties.AccessLog();
        p.setEnabled(true);
        p.setSlowThreshold(Duration.ofMillis(0));
        return p;
    }

    @Test
    void logs_method_uri_and_status() throws Exception {
        AccessLogFilter filter = new AccessLogFilter(props());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/things");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(appender.list).isNotEmpty();
        String message = appender.list.get(0).getFormattedMessage();
        assertThat(message).contains("GET").contains("/api/things").contains("200");
    }

    @Test
    void slow_request_is_logged_at_warn() throws Exception {
        // 阈值 0ms，任何请求都算慢
        AccessLogFilter filter = new AccessLogFilter(props());

        filter.doFilter(new MockHttpServletRequest("GET", "/api/slow"),
                        new MockHttpServletResponse(), new MockFilterChain());

        assertThat(appender.list).anySatisfy(e -> assertThat(e.getLevel()).isEqualTo(Level.WARN));
    }

    @Test
    void excluded_paths_are_not_logged() throws Exception {
        ShepherdWebProperties.AccessLog p = props();
        p.setExcludePaths(List.of("/actuator/**"));
        AccessLogFilter filter = new AccessLogFilter(p);

        filter.doFilter(new MockHttpServletRequest("GET", "/actuator/health"),
                        new MockHttpServletResponse(), new MockFilterChain());

        assertThat(appender.list).isEmpty();
    }

    @Test
    void disabled_filter_logs_nothing() throws Exception {
        ShepherdWebProperties.AccessLog p = props();
        p.setEnabled(false);
        AccessLogFilter filter = new AccessLogFilter(p);

        filter.doFilter(new MockHttpServletRequest("GET", "/api/things"),
                        new MockHttpServletResponse(), new MockFilterChain());

        assertThat(appender.list).isEmpty();
    }

    @Test
    void include_body_logs_masked_request_body() throws Exception {
        ShepherdWebProperties.AccessLog p = props();
        p.setIncludeBody(true);
        AccessLogFilter filter = new AccessLogFilter(p);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setContentType("application/json");
        request.setContent("{\"username\":\"admin\",\"password\":\"admin123\"}".getBytes());

        // ContentCachingRequestWrapper 只缓存被真正读过的字节，故让链把 body 读掉
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req,
                                 jakarta.servlet.ServletResponse res)
                    throws java.io.IOException, jakarta.servlet.ServletException {
                req.getInputStream().readAllBytes();
                super.doFilter(req, res);
            }
        };
        filter.doFilter(request, new MockHttpServletResponse(), chain);

        String message = appender.list.get(0).getFormattedMessage();
        assertThat(message).contains("admin").doesNotContain("admin123");
    }

    /** 不回写缓冲会让客户端收到空 body，这是 ContentCachingResponseWrapper 最典型的坑。 */
    @Test
    void include_body_still_delivers_the_response_to_the_client() throws Exception {
        ShepherdWebProperties.AccessLog p = props();
        p.setIncludeBody(true);
        AccessLogFilter filter = new AccessLogFilter(p);

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req,
                                 jakarta.servlet.ServletResponse res) throws java.io.IOException {
                res.getWriter().write("{\"code\":0}");
            }
        };
        filter.doFilter(new MockHttpServletRequest("GET", "/api/things"), response, chain);

        assertThat(response.getContentAsString()).isEqualTo("{\"code\":0}");
    }
}
