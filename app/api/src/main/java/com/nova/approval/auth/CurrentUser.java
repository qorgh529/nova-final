package com.nova.approval.auth;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * 현재 인증된 사용자 정보를 JWT에서 꺼낸다.
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Long id() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            throw new IllegalStateException("No authenticated user");
        }
        return Long.valueOf(jwt.getSubject());
    }
}
