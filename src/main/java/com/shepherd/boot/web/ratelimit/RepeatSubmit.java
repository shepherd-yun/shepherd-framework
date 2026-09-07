package com.shepherd.boot.web.ratelimit;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * 在 interval 时间窗内，同一用户对同一接口的相同参数只允许提交一次。
 *
 * <p>不在请求结束时主动释放锁——语义是「interval 内不许重复」，靠 TTL 自然过期。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RepeatSubmit {

    long interval() default 5;

    TimeUnit unit() default TimeUnit.SECONDS;

    /** 为空则用 CommonResultCode.REPEAT_SUBMIT 的默认文案。 */
    String message() default "";
}
