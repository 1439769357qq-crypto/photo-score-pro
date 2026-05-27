package com.example.photoscore.service;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WechatStateStore {

    private final Map<String, Long> cache = new ConcurrentHashMap<>();

    public String createState() {
        String state = UUID.randomUUID().toString().replace("-", "");
        cache.put(state, Instant.now().plusSeconds(300).toEpochMilli());
        return state;
    }

    public void verifyAndRemove(String state) {
        if (state == null || state.isBlank()) {
            throw new RuntimeException("微信登录状态参数为空");
        }

        Long expireAt = cache.remove(state);

        if (expireAt == null) {
            throw new RuntimeException("微信登录状态已失效");
        }

        if (System.currentTimeMillis() > expireAt) {
            throw new RuntimeException("微信登录状态已过期");
        }
    }
}