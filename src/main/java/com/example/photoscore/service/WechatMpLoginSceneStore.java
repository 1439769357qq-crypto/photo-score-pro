package com.example.photoscore.service;

import com.example.photoscore.pojo.AuthResponse;
import com.example.photoscore.pojo.WechatMpPollResponse;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WechatMpLoginSceneStore {

    private final Map<String, SceneRecord> cache = new ConcurrentHashMap<>();

    public String createScene(int expireMinutes) {
        String scene = UUID.randomUUID().toString().replace("-", "");
        long expireAt = Instant.now().plusSeconds(expireMinutes * 60L).toEpochMilli();
        cache.put(scene, SceneRecord.pending(expireAt));
        return scene;
    }

    public void confirm(String scene, AuthResponse auth) {
        SceneRecord old = cache.get(scene);

        if (old == null || System.currentTimeMillis() > old.expireAt) {
            throw new RuntimeException("微信登录二维码已过期，请刷新后重试");
        }

        cache.put(scene, SceneRecord.confirmed(old.expireAt, auth));
    }

    public void fail(String scene, String message) {
        SceneRecord old = cache.get(scene);

        if (old == null) {
            return;
        }

        cache.put(scene, SceneRecord.error(old.expireAt, message));
    }

    public WechatMpPollResponse poll(String scene) {
        if (scene == null || scene.isBlank()) {
            return WechatMpPollResponse.builder()
                    .status("ERROR")
                    .message("scene 为空")
                    .build();
        }

        SceneRecord record = cache.get(scene);

        if (record == null) {
            return WechatMpPollResponse.builder()
                    .status("EXPIRED")
                    .message("二维码不存在或已过期")
                    .build();
        }

        if (System.currentTimeMillis() > record.expireAt) {
            cache.remove(scene);
            return WechatMpPollResponse.builder()
                    .status("EXPIRED")
                    .message("二维码已过期")
                    .build();
        }

        if ("CONFIRMED".equals(record.status)) {
            cache.remove(scene);
            return WechatMpPollResponse.builder()
                    .status("CONFIRMED")
                    .message("微信登录成功")
                    .auth(record.auth)
                    .build();
        }

        if ("ERROR".equals(record.status)) {
            cache.remove(scene);
            return WechatMpPollResponse.builder()
                    .status("ERROR")
                    .message(record.message)
                    .build();
        }

        return WechatMpPollResponse.builder()
                .status("PENDING")
                .message("等待微信扫码确认")
                .build();
    }

    public boolean existsAndValid(String scene) {
        SceneRecord record = cache.get(scene);
        return record != null && System.currentTimeMillis() <= record.expireAt;
    }

    private record SceneRecord(String status, long expireAt, AuthResponse auth, String message) {

        static SceneRecord pending(long expireAt) {
            return new SceneRecord("PENDING", expireAt, null, null);
        }

        static SceneRecord confirmed(long expireAt, AuthResponse auth) {
            return new SceneRecord("CONFIRMED", expireAt, auth, null);
        }

        static SceneRecord error(long expireAt, String message) {
            return new SceneRecord("ERROR", expireAt, null, message);
        }
    }
}