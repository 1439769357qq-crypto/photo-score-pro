package com.example.photoscore.service.impl;

import com.example.photoscore.config.RenderMailApiProperties;
import com.example.photoscore.service.EmailSenderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

@Slf4j
@Primary
@Service
@Profile("render")
@RequiredArgsConstructor
public class RenderKeplarsEmailSenderServiceImpl implements EmailSenderService {

    private final RenderMailApiProperties properties;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Override
    public void sendRegisterCode(String email, String code) {
        String provider = safe(properties.getProvider()).toLowerCase();

        if ("console".equals(provider)) {
            log.warn("【Render邮件Console模式】to={}, code={}", maskEmail(email), code);
            return;
        }

        if (!"keplars".equals(provider)) {
            throw new RuntimeException("不支持的邮件 API 服务商 MAIL_API_PROVIDER=" + provider + "，可选值：keplars / console");
        }

        sendByKeplars(email, code);
    }

    private void sendByKeplars(String toEmail, String code) {
        validateConfig();

        try {
            String subject = "PhotoScore Pro 邮箱验证码";
            String html = buildHtml(code);

            Map<String, Object> payload = Map.of(
                    "to", List.of(toEmail),
                    "subject", subject,
                    "body", html
            );

            String body = objectMapper.writeValueAsString(payload);

            RenderMailApiProperties.Keplars keplars = properties.getKeplars();

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(keplars.getApiUrl()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body));

            String headerName = safe(keplars.getAuthHeaderName());
            String headerPrefix = safe(keplars.getAuthHeaderPrefix());
            String apiKey = normalizeApiKey(safe(keplars.getApiKey()), headerPrefix);

            if (!headerName.isBlank()) {
                if (headerPrefix.isBlank()) {
                    builder.header(headerName, apiKey);
                } else {
                    builder.header(headerName, headerPrefix + " " + apiKey);
                }
            }

            log.info("Keplars 邮件接口配置: url={}, headerName={}, prefix={}, key={}",
                    keplars.getApiUrl(),
                    headerName,
                    headerPrefix,
                    maskKey(apiKey));

            HttpResponse<String> response = httpClient.send(
                    builder.build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            int status = response.statusCode();

            if (status < 200 || status >= 300) {
                log.error("Keplars 邮件发送失败: status={}, body={}", status, response.body());
                throw new RuntimeException("邮箱验证码发送失败，Keplars 返回 HTTP "
                        + status + "：" + response.body());
            }

            log.info("邮箱验证码发送成功: provider=keplars, to={}", maskEmail(toEmail));

        } catch (Exception e) {
            log.error("Keplars 邮箱验证码发送异常: to={}, error={}", maskEmail(toEmail), e.getMessage(), e);
            throw new RuntimeException("邮箱验证码发送失败：" + e.getMessage());
        }
    }

    private String normalizeApiKey(String apiKey, String headerPrefix) {
        if (apiKey == null) {
            return "";
        }

        String key = apiKey.trim();

        if (!isBlank(headerPrefix)) {
            String prefix = headerPrefix.trim();

            if (key.toLowerCase().startsWith(prefix.toLowerCase() + " ")) {
                key = key.substring(prefix.length()).trim();
            }
        }

        if ((key.startsWith("\"") && key.endsWith("\""))
                || (key.startsWith("'") && key.endsWith("'"))) {
            key = key.substring(1, key.length() - 1).trim();
        }

        return key;
    }

    private String maskKey(String key) {
        if (key == null || key.length() <= 8) {
            return "****";
        }

        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }

    private String buildHtml(String code) {
        return """
                <div style="font-family:Arial,'Microsoft YaHei',sans-serif;line-height:1.8;color:#111827;">
                    <h2 style="margin:0 0 12px;">PhotoScore Pro 邮箱验证码</h2>
                    <p>你好，欢迎使用 PhotoScore Pro。</p>
                    <p>你的邮箱验证码是：</p>
                    <div style="font-size:28px;font-weight:800;letter-spacing:4px;background:#f3f4f6;border-radius:12px;padding:14px 18px;display:inline-block;">
                        %s
                    </div>
                    <p style="margin-top:18px;">验证码 5 分钟内有效，请勿泄露给他人。</p>
                    <p style="color:#6b7280;font-size:13px;">如果不是你本人操作，请忽略本邮件。</p>
                </div>
                """.formatted(escapeHtml(code));
    }

    private void validateConfig() {
        if (isBlank(properties.getFromEmail())) {
            throw new RuntimeException("Keplars 发件邮箱未配置，请检查 Render 环境变量 MAIL_FROM_EMAIL");
        }

        RenderMailApiProperties.Keplars keplars = properties.getKeplars();

        if (isBlank(keplars.getApiKey())) {
            throw new RuntimeException("Keplars API Key 未配置，请检查 Render 环境变量 KEPLARS_API_KEY");
        }

        if (isBlank(keplars.getApiUrl())) {
            throw new RuntimeException("Keplars 邮件 API 地址未配置，请检查 Render 环境变量 KEPLARS_EMAIL_API_URL");
        }
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return email;
        }

        String[] parts = email.split("@", 2);
        String name = parts[0];

        if (name.length() <= 2) {
            return name.charAt(0) + "***@" + parts[1];
        }

        return name.substring(0, 2) + "***@" + parts[1];
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private String escapeHtml(String s) {
        if (s == null) {
            return "";
        }

        return s
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}