package com.shepherdyun.boot.security;

import com.shepherdyun.boot.audit.AuditorProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

public class SecurityContextAuditorProvider implements AuditorProvider {

    @Override
    public Optional<Long> currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof LoginUser user)) {
            return Optional.empty();
        }
        return Optional.of(user.userId());
    }
}
