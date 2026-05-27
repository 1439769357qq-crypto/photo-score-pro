package com.example.photoscore.security;

import com.example.photoscore.config.AuthProperties;
import com.example.photoscore.pojo.UserAccount;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class JwtUtil {

    private final AuthProperties properties;
    private final ObjectMapper objectMapper;

    public String generateToken(UserAccount user) {
        try {
            Map<String, Object> header = new LinkedHashMap<>();
            header.put("alg", "HS256");
            header.put("typ", "JWT");

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("uid", user.getId());
            payload.put("username", user.getUsername());
            payload.put("exp", Instant.now().plusSeconds(properties.getExpireHours() * 3600L).toEpochMilli());

            String headerPart = base64Url(objectMapper.writeValueAsBytes(header));
            String payloadPart = base64Url(objectMapper.writeValueAsBytes(payload));
            String signature = sign(headerPart + "." + payloadPart);

            return headerPart + "." + payloadPart + "." + signature;
        } catch (Exception e) {
            throw new RuntimeException("生成登录Token失败", e);
        }
    }

    public JwtPayload parseToken(String token) {
        try {
            if (token == null || token.isBlank()) {
                throw new RuntimeException("Token为空");
            }

            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new RuntimeException("Token格式错误");
            }

            String signingInput = parts[0] + "." + parts[1];
            String expected = sign(signingInput);

            if (!constantTimeEquals(expected, parts[2])) {
                throw new RuntimeException("Token签名无效");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> payload = objectMapper.readValue(
                    Base64.getUrlDecoder().decode(parts[1]),
                    Map.class
            );

            Long uid = Long.valueOf(String.valueOf(payload.get("uid")));
            String username = String.valueOf(payload.get("username"));
            long exp = Long.parseLong(String.valueOf(payload.get("exp")));

            if (System.currentTimeMillis() > exp) {
                throw new RuntimeException("Token已过期");
            }

            return new JwtPayload(uid, username, exp);
        } catch (Exception e) {
            throw new RuntimeException("Token校验失败: " + e.getMessage());
        }
    }

    private String sign(String input) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(properties.getSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return base64Url(mac.doFinal(input.getBytes(StandardCharsets.UTF_8)));
    }

    private String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }

        byte[] x = a.getBytes(StandardCharsets.UTF_8);
        byte[] y = b.getBytes(StandardCharsets.UTF_8);

        if (x.length != y.length) {
            return false;
        }

        int result = 0;
        for (int i = 0; i < x.length; i++) {
            result |= x[i] ^ y[i];
        }

        return result == 0;
    }

    @Data
    @AllArgsConstructor
    public static class JwtPayload {
        private Long userId;
        private String username;
        private Long expireAt;
    }
}
