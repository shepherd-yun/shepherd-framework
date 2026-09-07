package com.shepherd.boot.web.ratelimit;

import com.shepherd.boot.web.ClientIps;
import com.shepherd.boot.web.code.CommonResultCode;
import com.shepherd.boot.web.exception.BizException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Aspect
public class RepeatSubmitAspect {

    private static final String KEY_PREFIX = "shepherd:repeat:";

    private final RepeatSubmitGuard guard;
    private final JsonMapper jsonMapper;

    public RepeatSubmitAspect(RepeatSubmitGuard guard, JsonMapper jsonMapper) {
        this.guard = guard;
        this.jsonMapper = jsonMapper;
    }

    @Around("@annotation(repeatSubmit)")
    public Object around(ProceedingJoinPoint pjp, RepeatSubmit repeatSubmit) throws Throwable {
        Duration ttl = Duration.ofMillis(repeatSubmit.unit().toMillis(repeatSubmit.interval()));
        String key = buildKey(pjp);
        if (!guard.tryAcquire(key, ttl)) {
            String message = repeatSubmit.message().isBlank()
                    ? CommonResultCode.REPEAT_SUBMIT.getMessage()
                    : repeatSubmit.message();
            throw new BizException(CommonResultCode.REPEAT_SUBMIT, message);
        }
        return pjp.proceed();
    }

    private String buildKey(ProceedingJoinPoint pjp) {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        String method = signature.getDeclaringTypeName() + "#" + signature.getName();
        return KEY_PREFIX + principal() + ":" + requestUri() + ":" + method + ":" + argsDigest(pjp.getArgs());
    }

    /** 有登录态用用户名，否则回落客户端 IP。 */
    private String principal() {
        @Nullable Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
            return auth.getName();
        }
        @Nullable HttpServletRequest request = currentRequest();
        return request == null ? "anonymous" : ClientIps.resolve(request);
    }

    private String requestUri() {
        @Nullable HttpServletRequest request = currentRequest();
        return request == null ? "-" : request.getRequestURI();
    }

    private static @Nullable HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes()
                instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    /**
     * 参数指纹。Servlet 相关与上传文件形参无法稳定序列化，直接跳过——
     * 它们不参与「是否同一次提交」的判定。
     */
    private String argsDigest(Object[] args) {
        List<Object> serializable = new ArrayList<>(args.length);
        for (Object arg : args) {
            if (arg instanceof HttpServletRequest
                    || arg instanceof HttpServletResponse
                    || arg instanceof MultipartFile) {
                continue;
            }
            serializable.add(arg);
        }
        String json = jsonMapper.writeValueAsString(serializable);
        return md5(json);
    }

    private static String md5(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("JRE 必须支持 MD5", e);
        }
    }
}
