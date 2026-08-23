package com.shepherd.boot.security;

import java.util.List;

public record LoginUser(Long userId, String username, List<String> roles, String jti) {}
