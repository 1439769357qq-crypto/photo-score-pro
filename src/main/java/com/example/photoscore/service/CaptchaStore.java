package com.example.photoscore.service;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CaptchaStore {

    private final Map<String, CaptchaRecord> cache = new ConcurrentHashMap<>();

    public String save(String code) {
        String id = UUID.randomUUID().toString().replace("-", "");
        cache.put(id, new CaptchaRecord(code.toLowerCase(Locale.ROOT), Instant.now().plusSeconds(300).toEpochMilli()));
        return id;
    }

    public boolean verifyAndRemove(String id, String code) {
        if (id == null || code == null) {
            return false;
        }

        CaptchaRecord record = cache.remove(id);
        if (record == null) {
            return false;
        }

        if (System.currentTimeMillis() > record.expireAt) {
            return false;
        }

        return record.code.equals(code.trim().toLowerCase(Locale.ROOT));
    }

    private record CaptchaRecord(String code, long expireAt) {}
}
