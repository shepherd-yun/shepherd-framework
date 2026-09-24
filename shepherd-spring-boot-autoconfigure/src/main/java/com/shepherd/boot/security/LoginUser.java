package com.shepherd.boot.security;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * 当前登录用户，由 {@link JwtAuthenticationFilter} 从 access token 解析后放进 SecurityContext 作为 principal。
 *
 * <p>{@code attributes} 是给下游业务携带自有身份字段的口子（用户类型、所属组织、可见事业部等）。
 * Starter 刻意不在这里定义任何业务字段：这些字段的含义只在具体业务域成立，一旦写进 Starter，
 * 所有下游都得背上别人的领域概念。下游应在自己的代码里包一层强类型对象，由它负责解释这些值。
 *
 * <p>attributes 原样进出 JWT，所以有三条约束：
 * <ul>
 *   <li>值会经过一次 JSON 往返，回来的是 JSON 类型而非原 Java 类型——{@code Long} 可能变成
 *       {@code Integer}，自定义对象变成 {@code Map}。读取数值请按 {@link Number} 取，或用 {@link #attributeLong}。</li>
 *   <li>JWT 只是 Base64 编码、未加密，不要放手机号等敏感信息；也不要放大列表，每个请求都会带着它。</li>
 *   <li>值在 token 过期前不会刷新。权限被收回时，要么等 token 过期，要么经 {@link LoginTokenStore} 踢下线。</li>
 * </ul>
 */
public record LoginUser(Long userId, String username, List<String> roles, String jti,
                        Map<String, Object> attributes) {

    public LoginUser {
        roles = List.copyOf(roles);
        // Map.copyOf 拒绝 null 值：JSON 往返后 null 与「键不存在」无法区分，干脆不允许
        attributes = Map.copyOf(attributes);
    }

    public LoginUser(Long userId, String username, List<String> roles, String jti) {
        this(userId, username, roles, jti, Map.of());
    }

    public @Nullable Object attribute(String key) {
        return attributes.get(key);
    }

    /**
     * 按数值读取 attribute，吸收 JSON 往返带来的 {@code Integer}/{@code Long} 差异。
     *
     * @throws IllegalStateException 值存在但不是数值
     */
    public @Nullable Long attributeLong(String key) {
        Object v = attributes.get(key);
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        throw new IllegalStateException("LoginUser attribute '" + key + "' 不是数值：" + v.getClass().getName());
    }
}
