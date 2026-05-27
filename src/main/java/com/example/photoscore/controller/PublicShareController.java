package com.example.photoscore.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Method;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class PublicShareController {

    private static final int MAX_HTML_LENGTH = 2_000_000;
    private static final Pattern SCRIPT_PATTERN = Pattern.compile("(?is)<script\\b[^>]*>.*?</script>");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    @PostMapping(value = "/api/workspace/share/customer-page", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> createCustomerPage(@RequestBody CustomerShareRequest requestBody,
                                                  HttpServletRequest request) {
        LoginUser loginUser = currentLoginUser();

        String html = normalizeHtml(requestBody.getHtml());
        String token = newToken();
        String title = defaultIfBlank(requestBody.getTitle(), "PhotoScore Pro 客户照片查看页");
        String clientName = defaultIfBlank(requestBody.getClientName(), "未填写");
        String version = defaultIfBlank(requestBody.getVersion(), "v1");
        String metaJson = toJsonQuietly(requestBody.getMeta());

        jdbcTemplate.update("""
                INSERT INTO ps_public_share
                (token, user_id, username, title, client_name, share_version, html_content, meta_json, expires_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, DATE_ADD(NOW(), INTERVAL 30 DAY))
                """,
                token,
                loginUser.userId(),
                loginUser.username(),
                title,
                clientName,
                version,
                html,
                metaJson
        );

        String shareUrl = buildBaseUrl(request) + "/share/customer/" + token;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", token);
        data.put("shareUrl", shareUrl);
        data.put("expiresInDays", 30);
        data.put("needLoginForCustomer", false);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 200);
        result.put("message", "success");
        result.put("data", data);
        return result;
    }

    @GetMapping(value = "/share/customer/{token}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> viewCustomerPage(@PathVariable String token) {
        String html;
        try {
            html = jdbcTemplate.queryForObject("""
                    SELECT html_content
                    FROM ps_public_share
                    WHERE token = ?
                      AND (expires_at IS NULL OR expires_at > NOW())
                    LIMIT 1
                    """, String.class, token);
        } catch (EmptyResultDataAccessException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.TEXT_HTML)
                    .body(notFoundHtml());
        }

        jdbcTemplate.update("UPDATE ps_public_share SET view_count = view_count + 1 WHERE token = ?", token);

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePublic())
                .header("X-Frame-Options", "SAMEORIGIN")
                .body(html);
    }

    private LoginUser currentLoginUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(String.valueOf(authentication.getPrincipal()))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        String username = String.valueOf(authentication.getPrincipal());
        long userId = extractUserId(authentication.getDetails());
        return new LoginUser(userId, username);
    }

    private long extractUserId(Object details) {
        if (details == null) return 0L;
        try {
            Method method = details.getClass().getMethod("getUserId");
            Object value = method.invoke(details);
            if (value instanceof Number number) return number.longValue();
            if (value != null && StringUtils.hasText(String.valueOf(value))) return Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
        }
        return 0L;
    }

    private String normalizeHtml(String rawHtml) {
        if (!StringUtils.hasText(rawHtml)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "客户查看页 HTML 不能为空");
        }
        String html = rawHtml.trim();
        if (html.length() > MAX_HTML_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "客户查看页内容过大，请减少交付清单或不要内嵌大图");
        }
        if (!html.toLowerCase().contains("<html")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "客户查看页 HTML 格式不完整");
        }
        // 客户页只需要展示，不需要执行脚本。移除脚本，降低公开分享页风险。
        return SCRIPT_PATTERN.matcher(html).replaceAll("");
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String buildBaseUrl(HttpServletRequest request) {
        String proto = firstNonBlank(request.getHeader("X-Forwarded-Proto"), request.getScheme(), "https");
        String host = firstNonBlank(request.getHeader("X-Forwarded-Host"), request.getHeader("Host"));
        if (!StringUtils.hasText(host)) {
            int port = request.getServerPort();
            boolean defaultPort = ("http".equals(proto) && port == 80) || ("https".equals(proto) && port == 443);
            host = request.getServerName() + (defaultPort ? "" : ":" + port);
        }
        return proto + "://" + host;
    }

    private String firstNonBlank(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (StringUtils.hasText(value)) return value.trim();
        }
        return "";
    }

    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String toJsonQuietly(Object value) {
        try {
            return value == null ? null : objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
    }

    private String notFoundHtml() {
        return """
                <!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1.0\"><title>分享已失效</title>
                <style>body{font-family:Arial,'Microsoft YaHei',sans-serif;background:#f8fafc;color:#111827;display:grid;place-items:center;min-height:100vh;margin:0}.card{background:#fff;border:1px solid #e5e7eb;border-radius:24px;padding:32px;max-width:520px;box-shadow:0 20px 60px rgba(15,23,42,.10)}h1{margin:0 0 12px;font-size:24px}p{line-height:1.75;color:#64748b}</style></head>
                <body><div class=\"card\"><h1>客户查看页不存在或已过期</h1><p>请联系摄影师重新生成交付查看链接。</p></div></body></html>
                """;
    }

    private record LoginUser(long userId, String username) {}

    public static class CustomerShareRequest {
        private String title;
        private String clientName;
        private String version;
        private String html;
        private Object meta;

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getClientName() { return clientName; }
        public void setClientName(String clientName) { this.clientName = clientName; }
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        public String getHtml() { return html; }
        public void setHtml(String html) { this.html = html; }
        public Object getMeta() { return meta; }
        public void setMeta(Object meta) { this.meta = meta; }
    }
}
