package com.example.photoscore.security;

import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;

@Component
@RequiredArgsConstructor
public class AdminAccessService {

    private static final String DEFAULT_ADMINS = "admin,tianjiao";

    private final Environment environment;

    public boolean isAdmin(String username) {
        if (username == null || username.isBlank()) {
            return false;
        }

        return Arrays.stream(adminUsers().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .anyMatch(s -> s.equalsIgnoreCase(username.trim()));
    }

    public boolean isCurrentUserAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        return isAdmin(authentication.getName());
    }

    public void requireCurrentUserAdmin() {
        if (!isCurrentUserAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有管理员可以访问该数据");
        }
    }

    private String adminUsers() {
        String envValue = environment.getProperty("PHOTOSCORE_ADMIN_USERS");
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }
        return environment.getProperty("photoscore.admin.users", DEFAULT_ADMINS);
    }
}
