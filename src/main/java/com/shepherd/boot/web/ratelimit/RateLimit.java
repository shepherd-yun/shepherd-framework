package com.shepherd.boot.web.ratelimit;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/** 接口级限流：period 时间窗内最多允许 count 次调用。 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    /** 窗口内允许的最大次数。 */
    int count() default 100;

    long period() default 60;

    TimeUnit unit() default TimeUnit.SECONDS;

    Dimension dimension() default Dimension.IP;

    /** 为空则用 CommonResultCode.RATE_LIMITED 的默认文案。 */
    String message() default "";

    enum Dimension {
        /** 按客户端 IP。注意 IP 来自可伪造的 header，见 ClientIps 的安全提示。 */
        IP,
        /** 按登录用户；取不到登录态时回落 IP。 */
        USER,
        /** 全局共享一个计数器。 */
        GLOBAL
    }
}
