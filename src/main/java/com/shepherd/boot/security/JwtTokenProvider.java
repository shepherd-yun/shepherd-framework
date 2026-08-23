package com.shepherd.boot.security;

import com.shepherd.boot.core.exception.BizException;
import com.shepherd.boot.core.result.CommonResultCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

public class JwtTokenProvider {

    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;
    private final ShepherdSecurityProperties.Jwt props;

    public JwtTokenProvider(ShepherdSecurityProperties.Jwt props) {
        String secret = props.getSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "shepherd.security.jwt.secret 未配置。请设置一个至少 32 字节的密钥"
                    + "（建议经环境变量注入），或设置 shepherd.security.enabled=false 关闭鉴权。");
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
        return Jwts.builder()
                .id(user.jti())
                .subject(String.valueOf(user.userId()))
                .claim("username", user.username())
                .claim("roles", user.roles())
                .claim("type", "access")
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
            Claims c = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
            if (!"access".equals(c.get("type", String.class))) {
                throw new BizException(CommonResultCode.TOKEN_INVALID);
            }
            List<String> roles = c.get("roles", List.class);
            if (roles == null) {
                roles = List.of();
            }
            return new LoginUser(Long.valueOf(c.getSubject()), c.get("username", String.class), roles, c.getId());
        } catch (ExpiredJwtException e) {
            throw new BizException(CommonResultCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BizException(CommonResultCode.TOKEN_INVALID);
        }
    }
}
