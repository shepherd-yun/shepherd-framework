package com.shepherd.boot.security;

/**
 * refresh token 解析结果。只有身份与 jti，不带 roles/attributes：
 * 换发 access token 时应按 userId 重新加载这些字段，权限变更才能在刷新时生效。
 */
public record RefreshClaims(Long userId, String jti) {
}
