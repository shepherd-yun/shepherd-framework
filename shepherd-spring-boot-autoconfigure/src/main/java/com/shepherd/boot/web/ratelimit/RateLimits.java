package com.shepherd.boot.web.ratelimit;

import java.lang.annotation.*;

/** {@link RateLimit} 的容器注解，由编译器在叠加多个 {@code @RateLimit} 时自动生成，无需手写。 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimits {

    RateLimit[] value();
}
