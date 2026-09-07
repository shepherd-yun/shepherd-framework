package com.shepherd.boot.web.async;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class MdcTaskDecoratorTest {

    private final MdcTaskDecorator decorator = new MdcTaskDecorator();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void submitting_thread_mdc_is_visible_in_executing_thread() throws Exception {
        MDC.put("traceId", "abc123");
        AtomicReference<String> seen = new AtomicReference<>();

        Runnable decorated = decorator.decorate(() -> seen.set(MDC.get("traceId")));

        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            pool.submit(decorated).get();
        } finally {
            pool.shutdown();
        }

        assertThat(seen.get()).isEqualTo("abc123");
    }

    @Test
    void executing_thread_mdc_is_left_clean_afterwards() throws Exception {
        MDC.put("traceId", "outer");
        Runnable decorated = decorator.decorate(() -> MDC.put("traceId", "inner"));

        ExecutorService pool = Executors.newSingleThreadExecutor();
        AtomicReference<String> after = new AtomicReference<>("sentinel");
        try {
            // 池线程上的既有值是上一个任务的残留（污染），任务跑完不该把任何值留下
            pool.submit(() -> MDC.put("traceId", "pre-existing")).get();
            pool.submit(decorated).get();
            pool.submit(() -> after.set(MDC.get("traceId"))).get();
        } finally {
            pool.shutdown();
        }

        assertThat(after.get()).isNull();
    }

    @Test
    void empty_submitting_mdc_clears_executing_thread_context() throws Exception {
        MDC.clear();
        Runnable decorated = decorator.decorate(() -> { });

        ExecutorService pool = Executors.newSingleThreadExecutor();
        AtomicReference<String> seen = new AtomicReference<>("sentinel");
        try {
            pool.submit(() -> MDC.put("traceId", "stale")).get();
            pool.submit(decorated).get();
            pool.submit(() -> seen.set(MDC.get("traceId"))).get();
        } finally {
            pool.shutdown();
        }

        // 提交线程没有 MDC 时，不能让上一个任务的残留值泄漏进来
        assertThat(seen.get()).isNull();
    }

    @Test
    void mdc_is_restored_even_when_task_throws() throws Exception {
        MDC.put("traceId", "outer");
        Runnable decorated = decorator.decorate(() -> {
            throw new IllegalStateException("boom");
        });

        ExecutorService pool = Executors.newSingleThreadExecutor();
        AtomicReference<String> after = new AtomicReference<>("sentinel");
        try {
            pool.submit(decorated);
            pool.submit(() -> after.set(MDC.get("traceId"))).get();
        } finally {
            pool.shutdown();
        }

        assertThat(after.get()).isNull();
    }
}
