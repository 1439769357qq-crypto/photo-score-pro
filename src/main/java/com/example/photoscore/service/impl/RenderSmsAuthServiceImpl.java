package com.example.photoscore.service.impl;

import com.example.photoscore.config.RenderGuoyangSmsProperties;
import com.example.photoscore.service.SmsAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Primary
@Service
@Profile("render")
@RequiredArgsConstructor
public class RenderSmsAuthServiceImpl implements SmsAuthService {

    private final RenderGuoyangSmsProperties properties;

    private final SecureRandom random = new SecureRandom();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private final Map<String, SmsCodeRecord> codeCache = new ConcurrentHashMap<>();
    private final Map<String, Long> phoneNextSendTime = new ConcurrentHashMap<>();
    private final Map<String, Long> ipNextSendTime = new ConcurrentHashMap<>();

    @Override
    public void sendCode(String phone, String ip) {
        if (!properties.isEnabled()) {
            throw new RuntimeException("短信验证码功能未启用");
        }

        checkFrequency(phone, ip);

        String provider = safe(properties.getProvider()).toLowerCase();
        String code = createCode(provider);

        switch (provider) {
            case "fixed" -> {
                saveLocalCode(phone, code, ip);
                log.warn("【Render短信固定验证码模式】phone={}, code={}", maskPhone(phone), code);
            }
            case "console" -> {
                saveLocalCode(phone, code, ip);
                log.warn("【Render短信控制台模式】phone={}, code={}", maskPhone(phone), code);
            }
            case "guoyang" -> {
                sendByGuoyang(phone, code);
                saveLocalCode(phone, code, ip);
            }
            default -> throw new RuntimeException("未知 Render 短信服务商 RENDER_SMS_PROVIDER="
                    + provider + "，可选值：fixed / console / guoyang");
        }
    }

    @Override
    public void verifyCode(String phone, String code) {
        SmsCodeRecord record = codeCache.get(phone);

        if (record == null) {
            throw new RuntimeException("验证码不存在或已过期");
        }

        if (System.currentTimeMillis() > record.expireAt) {
            codeCache.remove(phone);
            throw new RuntimeException("验证码已过期");
        }

        if (record.failCount >= 5) {
            codeCache.remove(phone);
            throw new RuntimeException("验证码错误次数过多，请重新获取");
        }

        if (code == null || !record.code.equals(code.trim())) {
            codeCache.put(phone, new SmsCodeRecord(
                    record.code,
                    record.expireAt,
                    record.failCount + 1
            ));
            throw new RuntimeException("验证码错误");
        }

        codeCache.remove(phone);
    }

    private void sendByGuoyang(String phone, String code) {
        RenderGuoyangSmsProperties.Guoyang guoyang = properties.getGuoyang();

        validateGuoyangConfig(guoyang);

        try {
            String param = "**code**:" + code + ",**minute**:" + guoyang.getMinute();

            String query = "mobile=" + url(phone)
                    + "&templateId=" + url(guoyang.getTemplateId())
                    + "&smsSignId=" + url(guoyang.getSmsSignId())
                    + "&param=" + url(param);

            String requestUrl = guoyang.getUrl();

            if (requestUrl.contains("?")) {
                requestUrl = requestUrl + "&" + query;
            } else {
                requestUrl = requestUrl + "?" + query;
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(requestUrl))
                    .timeout(Duration.ofSeconds(properties.getRequestTimeoutSeconds()))
                    .header("Authorization", "APPCODE " + guoyang.getAppCode().trim())
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            int status = response.statusCode();
            String body = response.body();

            log.info("国阳云短信接口响应: status={}, body={}", status, body);

            if (status < 200 || status >= 300) {
                throw new RuntimeException("国阳云短信发送失败，HTTP " + status + "：" + body);
            }

            if (looksLikeFailure(body)) {
                throw new RuntimeException("国阳云短信发送失败：" + body);
            }

            log.info("国阳云短信验证码发送成功: phone={}", maskPhone(phone));

        } catch (Exception e) {
            log.error("国阳云短信验证码发送异常: phone={}, error={}", maskPhone(phone), e.getMessage(), e);
            throw new RuntimeException("短信验证码发送失败：" + e.getMessage());
        }
    }

    private void validateGuoyangConfig(RenderGuoyangSmsProperties.Guoyang guoyang) {
        if (isBlank(guoyang.getUrl())) {
            throw new RuntimeException("国阳云短信接口地址未配置，请检查 GUOYANG_SMS_URL");
        }

        if (isBlank(guoyang.getAppCode())) {
            throw new RuntimeException("国阳云 AppCode 未配置，请检查 GUOYANG_SMS_APPCODE");
        }

        if (isBlank(guoyang.getSmsSignId())) {
            throw new RuntimeException("国阳云 smsSignId 未配置，请检查 GUOYANG_SMS_SIGN_ID");
        }

        if (isBlank(guoyang.getTemplateId())) {
            throw new RuntimeException("国阳云 templateId 未配置，请检查 GUOYANG_SMS_TEMPLATE_ID");
        }
    }

    /**
     * 不同阿里云市场服务商返回结构不统一，这里只做明显失败判断。
     * 如果你的接口成功响应有固定格式，后续可以再精确化。
     */
    private boolean looksLikeFailure(String body) {
        if (body == null || body.isBlank()) {
            return false;
        }

        String lower = body.toLowerCase();

        return lower.contains("\"success\":false")
                || lower.contains("\"code\":-")
                || lower.contains("\"error\"")
                || lower.contains("\"errmsg\"")
                || lower.contains("\"error_msg\"")
                || lower.contains("invalid")
                || lower.contains("fail");
    }

    private String createCode(String provider) {
        if ("fixed".equals(provider)) {
            String fixed = properties.getFixedCode();

            if (fixed == null || !fixed.matches("^\\d{4,8}$")) {
                throw new RuntimeException("固定验证码 RENDER_SMS_FIXED_CODE 配置错误，必须是4到8位数字");
            }

            return fixed;
        }

        return String.format("%06d", random.nextInt(1_000_000));
    }

    private void checkFrequency(String phone, String ip) {
        long now = System.currentTimeMillis();

        Long phoneNext = phoneNextSendTime.get(phone);
        if (phoneNext != null && now < phoneNext) {
            long seconds = Math.max(1, (phoneNext - now) / 1000);
            throw new RuntimeException("验证码发送太频繁，请 " + seconds + " 秒后再试");
        }

        if (!isBlank(ip)) {
            Long ipNext = ipNextSendTime.get(ip);
            if (ipNext != null && now < ipNext) {
                long seconds = Math.max(1, (ipNext - now) / 1000);
                throw new RuntimeException("当前IP发送太频繁，请 " + seconds + " 秒后再试");
            }
        }
    }

    private void saveLocalCode(String phone, String code, String ip) {
        long expireAt = Instant.now()
                .plusSeconds(properties.getCodeExpireMinutes() * 60L)
                .toEpochMilli();

        long nextSendAt = Instant.now()
                .plusSeconds(properties.getResendIntervalSeconds())
                .toEpochMilli();

        codeCache.put(phone, new SmsCodeRecord(code, expireAt, 0));
        phoneNextSendTime.put(phone, nextSendAt);

        if (!isBlank(ip)) {
            ipNextSendTime.put(ip, nextSendAt);
        }
    }

    private String url(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }

        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private record SmsCodeRecord(String code, long expireAt, int failCount) {
    }
}