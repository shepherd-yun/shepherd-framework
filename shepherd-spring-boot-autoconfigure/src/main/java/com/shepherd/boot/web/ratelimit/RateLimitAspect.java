package com.shepherd.boot.web.ratelimit;

import com.shepherd.boot.web.ClientIps;
import com.shepherd.boot.web.code.CommonResultCode;
import com.shepherd.boot.web.exception.BizException;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;

@Aspect
public class RateLimitAspect {

    private static final String KEY_PREFIX = "shepherd:rate:";

    private final RateLimiter limiter;

    public RateLimitAspect(RateLimiter limiter) {
        this.limiter = limiter;
    }

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint pjp, RateLimit rateLimit) throws Throwable {
        Duration window = Duration.ofMillis(rateLimit.unit().toMillis(rateLimit.period()));
        String key = buildKey(pjp, rateLimit.dimension());

        long current = limiter.increment(key, window);
        if (current > rateLimit.count()) {
            String message = rateLimit.message().isBlank()
                    ? CommonResultCode.RATE_LIMITED.getMessage()
                    : rateLimit.message();
            throw new BizException(CommonResultCode.RATE_LIMITED, message);
        }
        return pjp.proceed();
    }

    private String buildKey(ProceedingJoinPoint pjp, RateLimit.Dimension dimension) {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        String method = signature.getDeclaringTypeName() + "#" + signature.getName();
        return KEY_PREFIX + method + ":" + dimensionValue(dimension);
    }

    private String dimensionValue(RateLimit.Dimension dimension) {
        return switch (dimension) {
            case GLOBAL -> "global";
            case USER -> currentUser();
            case IP -> currentIp();
        };
    }

    /** 取不到登录态时回落 IP：否则所有匿名请求会共享一个计数器，互相误伤。 */
    private String currentUser() {
        @Nullable Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
            return "user:" + auth.getName();
        }
        return currentIp();
    }

    private String currentIp() {
        if (RequestContextHolder.getRequestAttributes()
                instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            return "ip:" + ClientIps.resolve(request);
        }
        return "ip:unknown";
    }
}
