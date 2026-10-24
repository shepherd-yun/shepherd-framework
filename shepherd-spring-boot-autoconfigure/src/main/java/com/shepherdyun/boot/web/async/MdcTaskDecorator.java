package com.shepherdyun.boot.web.async;

import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;

/**
 * 把提交线程的 MDC（含 TraceIdFilter 放进去的 traceId）透传进执行线程，
 * 让 @Async 方法里的日志仍能与发起请求串起来。
 *
 * <p>执行前覆盖、执行后清空，两头都不给线程池留残留：池里的线程会被复用，
 * 上一个任务留下的 traceId 若泄漏进下一个任务，日志会串到错误的请求上。
 * 所以 finally 里是无条件 clear，而不是「还原执行线程原有的 MDC」——
 * 池线程上的既有 MDC 本身就是污染，没有保留价值。
 */
public class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        @Nullable Map<String, String> submitterContext = MDC.getCopyOfContextMap();
        return () -> {
            if (submitterContext == null || submitterContext.isEmpty()) {
                MDC.clear();
            } else {
                MDC.setContextMap(submitterContext);
            }
            try {
                runnable.run();
            } finally {
                MDC.clear();
            }
        };
    }
}
