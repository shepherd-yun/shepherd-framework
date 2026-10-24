package com.shepherdyun.boot.web.ratelimit;

import com.shepherdyun.boot.web.ClientIps;
import com.shepherdyun.boot.web.code.CommonResultCode;
import com.shepherdyun.boot.web.exception.BizException;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Aspect
public class RateLimitAspect {

    private static final String KEY_PREFIX = "shepherd:rate:";

    private final RateLimiter limiter;
    private final SpelExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNames = new DefaultParameterNameDiscoverer();
    private final Map<String, Expression> expressions = new ConcurrentHashMap<>();

    public RateLimitAspect(RateLimiter limiter) {
        this.limiter = limiter;
    }

    /** 叠加多个 {@code @RateLimit} 时方法上只剩容器注解 {@link RateLimits}，两种都得拦。 */
    @Around("@annotation(com.shepherdyun.boot.web.ratelimit.RateLimit)"
            + " || @annotation(com.shepherdyun.boot.web.ratelimit.RateLimits)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        Method method = resolveMethod(pjp);
        for (RateLimit rateLimit : AnnotatedElementUtils.findMergedRepeatableAnnotations(method, RateLimit.class)) {
            check(pjp, method, rateLimit);
        }
        return pjp.proceed();
    }

    private void check(ProceedingJoinPoint pjp, Method method, RateLimit rateLimit) {
        Duration window = Duration.ofMillis(rateLimit.unit().toMillis(rateLimit.period()));
        String key = buildKey(pjp, method, rateLimit, window);

        long current = limiter.increment(key, window);
        if (current > rateLimit.count()) {
            String message = rateLimit.message().isBlank()
                    ? CommonResultCode.RATE_LIMITED.getMessage()
                    : rateLimit.message();
            throw new BizException(CommonResultCode.RATE_LIMITED, message);
        }
    }

    /**
     * key 里带上「次数/窗口」：叠加的多个限流若共用一个计数器，每次调用会被重复累加，
     * 实际放行次数只剩配置值的几分之一。
     */
    private String buildKey(ProceedingJoinPoint pjp, Method method, RateLimit rateLimit, Duration window) {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        String methodId = signature.getDeclaringTypeName() + "#" + signature.getName();
        return KEY_PREFIX + methodId + ":" + rateLimit.count() + "/" + window + ":"
                + dimensionValue(pjp, method, rateLimit);
    }

    private String dimensionValue(ProceedingJoinPoint pjp, Method method, RateLimit rateLimit) {
        if (rateLimit.dimension() != RateLimit.Dimension.KEY && !rateLimit.key().isBlank()) {
            throw new IllegalStateException(method + " 的 @RateLimit 写了 key 表达式，但 dimension 不是"
                    + " Dimension.KEY——表达式不会生效，请补上 dimension = Dimension.KEY 或删掉 key");
        }
        return switch (rateLimit.dimension()) {
            case GLOBAL -> "global";
            case USER -> currentUser();
            case IP -> currentIp();
            case KEY -> keyValue(pjp, method, rateLimit.key());
        };
    }

    /** 拿实现类上的方法：接口代理时签名里是接口方法，注解与参数名都在实现类上。 */
    private static Method resolveMethod(ProceedingJoinPoint pjp) {
        Method method = ((MethodSignature) pjp.getSignature()).getMethod();
        Object target = pjp.getTarget();
        return target == null ? method : AopUtils.getMostSpecificMethod(method, target.getClass());
    }

    /**
     * 值取 SHA-256：来自请求参数，可能是手机号等个人信息，也可能是任意长的字符串，
     * 两者都不该原样进 Redis key。取不到值时回落 IP，理由同 {@link #currentUser()}。
     */
    private String keyValue(ProceedingJoinPoint pjp, Method method, String keyExpression) {
        if (keyExpression.isBlank()) {
            throw new IllegalStateException(method + " 的 @RateLimit(dimension = Dimension.KEY) 必须指定 key 表达式，"
                    + "如 key = \"#phone\"");
        }
        Expression expression = expressions.computeIfAbsent(keyExpression, parser::parseExpression);
        var context = new MethodBasedEvaluationContext(null, method, pjp.getArgs(), parameterNames);
        @Nullable Object value = expression.getValue(context);
        if (value == null || value.toString().isBlank()) {
            return currentIp();
        }
        return "key:" + sha256(value.toString());
    }

    private static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("JRE 必须支持 SHA-256", e);
        }
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
