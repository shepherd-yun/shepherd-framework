package com.shepherd.boot.web.ratelimit;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * 接口级限流：period 时间窗内最多允许 count 次调用。
 *
 * <p>可在同一方法上叠加多个，各自独立计数、全部满足才放行，例如短信验证码
 * 「同一手机号 60 秒 1 次 + 每天 10 次」。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Repeatable(RateLimits.class)
public @interface RateLimit {

    /** 窗口内允许的最大次数。 */
    int count() default 100;

    long period() default 60;

    TimeUnit unit() default TimeUnit.SECONDS;

    Dimension dimension() default Dimension.IP;

    /**
     * {@link Dimension#KEY} 时的计数依据，SpEL，对方法参数求值，如 {@code #phone}、{@code #req.phone}。
     * 参数名依赖编译参数 {@code -parameters}（继承 shepherd-parent 即已开启），否则改用 {@code #p0}。
     * 只能与 {@link Dimension#KEY} 一起使用，其余维度写了会直接抛异常。
     */
    String key() default "";

    /** 为空则用 CommonResultCode.RATE_LIMITED 的默认文案。 */
    String message() default "";

    enum Dimension {
        /** 按客户端 IP。注意 IP 来自可伪造的 header，见 ClientIps 的安全提示。 */
        IP,
        /** 按登录用户；取不到登录态时回落 IP。 */
        USER,
        /** 全局共享一个计数器。 */
        GLOBAL,
        /** 按 {@link #key()} 表达式的值（如手机号）；值为 null 或空白时回落 IP。 */
        KEY
    }
}
