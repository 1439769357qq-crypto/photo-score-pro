package com.example.photoscore.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Component
public class WorkspaceCurrentUser {
    public Long userId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("请先登录");
        }

        Object details = authentication.getDetails();
        if (details instanceof JwtUtil.JwtPayload payload) {
            return payload.getUserId();
        }

        Long id = tryReadLong(details, "getUserId");
        if (id != null) return id;

        id = tryReadLong(authentication.getPrincipal(), "getUserId");
        if (id != null) return id;

        throw new RuntimeException("无法获取当前用户ID，请确认 JwtAuthenticationFilter 已执行 authentication.setDetails(payload)");
    }

    private Long tryReadLong(Object target, String methodName) {
        if (target == null) return null;
        try {
            Method method = target.getClass().getMethod(methodName);
            Object value = method.invoke(target);
            if (value instanceof Number n) return n.longValue();
            if (value != null) return Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {}
        return null;
    }
}
