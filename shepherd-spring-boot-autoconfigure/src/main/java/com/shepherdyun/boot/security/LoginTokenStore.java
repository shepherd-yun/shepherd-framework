package com.shepherdyun.boot.security;

/**
 * 登录态存储。JWT 本身无状态，此存储用于支持登出与踢人：
 * 过滤器每次请求校验 jti 是否仍有效。
 */
public interface LoginTokenStore {

    void save(Long userId, String jti, long ttlSeconds);

    boolean isValid(Long userId, String jti);

    void revoke(Long userId, String jti);
}
