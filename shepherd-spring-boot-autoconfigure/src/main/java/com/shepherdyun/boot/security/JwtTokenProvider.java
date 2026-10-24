package com.shepherdyun.boot.security;

import com.shepherdyun.boot.web.exception.BizException;
import com.shepherdyun.boot.web.code.CommonResultCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class JwtTokenProvider {

    private static final int MIN_SECRET_BYTES = 32;

    /**
     * {@link LoginUser#attributes()} 整体收进这一个 claim，而不是平铺到顶层：
     * 平铺会让下游的键与 {@code sub}/{@code exp}/{@code roles}/{@code type} 等撞名，悄悄覆盖掉安全相关字段。
     */
    static final String ATTRS_CLAIM = "attrs";

    private final SecretKey key;
    private final ShepherdSecurityProperties.Jwt props;

    public JwtTokenProvider(ShepherdSecurityProperties.Jwt props) {
        String secret = props.getSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "shepherd.security.jwt.secret 未配置。请设置一个至少 32 字节的密钥"
                    + "（建议经环境变量注入）；或设置 shepherd.security.enabled=false 关闭 Starter 的 JWT 安全链"
                    + "——注意这不等于关闭鉴权：只要 classpath 上仍有 spring-security，Spring Boot 会回落到"
                    + "其默认的 basic/form 登录链并生成一个随机密码。若要完全放行所有请求，需自行声明一个"
                    + "SecurityFilterChain Bean。");
        }
        int len = secret.getBytes(StandardCharsets.UTF_8).length;
        if (len < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "shepherd.security.jwt.secret 过短：当前 " + len + " 字节，HMAC-SHA256 至少需要 "
                    + MIN_SECRET_BYTES + " 字节。");
        }
        this.props = props;
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(LoginUser user) {
        Date now = new Date();
        var builder = Jwts.builder()
                .id(user.jti())
                .subject(String.valueOf(user.userId()))
                .claim("username", user.username())
                .claim("roles", user.roles())
                .claim("type", "access");
        if (!user.attributes().isEmpty()) {
            // 空时不写，token 保持与引入 attributes 之前完全一致
            builder.claim(ATTRS_CLAIM, user.attributes());
        }
        return builder
                .issuedAt(now)
                .expiration(new Date(now.getTime() + props.getAccessTtlSeconds() * 1000))
                .signWith(key)
                .compact();
    }

    public String generateRefreshToken(Long userId, String jti) {
        Date now = new Date();
        return Jwts.builder()
                .id(jti)
                .subject(String.valueOf(userId))
                .claim("type", "refresh")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + props.getRefreshTtlSeconds() * 1000))
                .signWith(key)
                .compact();
    }

    @SuppressWarnings("unchecked")
    public LoginUser parseAccess(String token) {
        try {
            Claims c = parseTyped(token, "access");
            List<String> roles = c.get("roles", List.class);
            if (roles == null) {
                roles = List.of();
            }
            Map<String, Object> attrs = c.get(ATTRS_CLAIM, Map.class);
            if (attrs == null) {
                attrs = Map.of();
            }
            return new LoginUser(Long.valueOf(c.getSubject()), c.get("username", String.class), roles, c.getId(),
                    attrs);
        } catch (ExpiredJwtException e) {
            throw new BizException(CommonResultCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BizException(CommonResultCode.TOKEN_INVALID);
        }
    }

    /**
     * 只验签与校验类型，不查 {@link LoginTokenStore}：jti 是否已被登出/踢下线由调用方判断，
     * 刷新流程见 README「刷新 access token」。
     */
    public RefreshClaims parseRefresh(String token) {
        try {
            Claims c = parseTyped(token, "refresh");
            return new RefreshClaims(Long.valueOf(c.getSubject()), c.getId());
        } catch (ExpiredJwtException e) {
            throw new BizException(CommonResultCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BizException(CommonResultCode.TOKEN_INVALID);
        }
    }

    /** 两种 token 同一把密钥签发，type 校验是唯一能把它们区分开的地方，不可省。 */
    private Claims parseTyped(String token, String expectedType) {
        Claims c = Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
        if (!expectedType.equals(c.get("type", String.class))) {
            throw new BizException(CommonResultCode.TOKEN_INVALID);
        }
        return c;
    }
}
