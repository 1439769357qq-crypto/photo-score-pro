package com.example.photoscore.service;

import com.example.photoscore.config.EmailAuthProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class EmailCodeStore {

    private final EmailAuthProperties properties;
    private final SecureRandom random = new SecureRandom();

    private final Map<String, EmailCodeRecord> emailCache = new ConcurrentHashMap<>();
    private final Map<String, Long> ipNextSendTime = new ConcurrentHashMap<>();

    public String createCode(String email, String ip) {
        long now = System.currentTimeMillis();

        EmailCodeRecord old = emailCache.get(email);
        if (old != null && now < old.nextSendAt) {
            long seconds = Math.max(1, (old.nextSendAt - now) / 1000);
            throw new RuntimeException("邮箱验证码发送太频繁，请 " + seconds + " 秒后再试");
        }

        Long ipNext = ipNextSendTime.get(ip);
        if (ipNext != null && now < ipNext) {
            long seconds = Math.max(1, (ipNext - now) / 1000);
            throw new RuntimeException("当前IP发送太频繁，请 " + seconds + " 秒后再试");
        }

        String code = String.format("%06d", random.nextInt(1_000_000));

        long expireAt = Instant.now()
                .plusSeconds(properties.getCodeExpireMinutes() * 60L)
                .toEpochMilli();

        long nextSendAt = Instant.now()
                .plusSeconds(properties.getResendIntervalSeconds())
                .toEpochMilli();

        emailCache.put(email, new EmailCodeRecord(code, expireAt, nextSendAt, 0));
        ipNextSendTime.put(ip, nextSendAt);

        return code;
    }

    public void verifyAndRemove(String email, String code) {
        EmailCodeRecord record = emailCache.get(email);

        if (record == null) {
            throw new RuntimeException("邮箱验证码不存在或已过期");
        }

        if (System.currentTimeMillis() > record.expireAt) {
            emailCache.remove(email);
            throw new RuntimeException("邮箱验证码已过期");
        }

        if (record.failCount >= 5) {
            emailCache.remove(email);
            throw new RuntimeException("邮箱验证码错误次数过多，请重新获取");
        }

        if (code == null || !record.code.equals(code.trim())) {
            emailCache.put(email, new EmailCodeRecord(
                    record.code,
                    record.expireAt,
                    record.nextSendAt,
                    record.failCount + 1
            ));
            throw new RuntimeException("邮箱验证码错误");
        }

        emailCache.remove(email);
    }

    private record EmailCodeRecord(String code, long expireAt, long nextSendAt, int failCount) {
    }
}