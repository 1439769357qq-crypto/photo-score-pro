package com.example.photoscore.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.photoscore.config.WechatLoginProperties;
import com.example.photoscore.mapper.UserAccountMapper;
import com.example.photoscore.pojo.*;
import com.example.photoscore.security.JwtUtil;
import com.example.photoscore.service.*;
import com.example.photoscore.util.CaptchaUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");

    private final UserAccountMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final CaptchaStore captchaStore;
    private final SmsAuthService smsAuthService;
    private final EmailCodeStore emailCodeStore;
    private final EmailSenderService emailSenderService;
    private final WechatStateStore wechatStateStore;
    private final WechatLoginProperties wechatProperties;
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;
    private final WechatMpLoginSceneStore wechatMpLoginSceneStore;


    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Override
    public CaptchaResponse createCaptcha() {
        CaptchaUtil.CaptchaImage image = CaptchaUtil.generate();
        String captchaId = captchaStore.save(image.code());

        return CaptchaResponse.builder()
                .captchaId(captchaId)
                .imageBase64(image.imageBase64())
                .build();
    }

    @Override
    public void sendSmsCode(SmsCodeRequest request, String ip) {
        requirePhone(request.getPhone());

        if (!captchaStore.verifyAndRemove(request.getCaptchaId(), request.getCaptchaCode())) {
            throw new RuntimeException("图形验证码错误或已过期");
        }

        smsAuthService.sendCode(request.getPhone(), ip);
    }

    @Override
    public void sendEmailCode(EmailCodeRequest request, String ip) {
        String email = safe(request.getEmail()).toLowerCase();

        requireEmail(email);

        if (!captchaStore.verifyAndRemove(request.getCaptchaId(), request.getCaptchaCode())) {
            throw new RuntimeException("图形验证码错误或已过期");
        }

        String code = emailCodeStore.createCode(email, ip);
        emailSenderService.sendRegisterCode(email, code);
    }

    @Override
    public AuthResponse register(RegisterRequest request) {
        String username = safe(request.getUsername());
        String password = safe(request.getPassword());
        String phone = safe(request.getPhone());



        requireUsername(username);
        requirePassword(password);
        requirePhone(phone);

        smsAuthService.verifyCode(phone, safe(request.getSmsCode()));

        if (findByUsername(username) != null) {
            throw new RuntimeException("用户名已被注册");
        }

        if (findByPhone(phone) != null) {
            throw new RuntimeException("手机号已被注册");
        }

        LocalDateTime now = LocalDateTime.now();

        UserAccount user = UserAccount.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode(password))
                .phone(phone)
                .status(1)
                .emailVerified(0)
                .createdTime(now)
                .updatedTime(now)
                .build();

        userMapper.insert(user);

        return buildAuthResponse(user);
    }

    @Override
    public AuthResponse emailRegister(EmailRegisterRequest request) {
        String username = safe(request.getUsername());
        String password = safe(request.getPassword());
        String email = safe(request.getEmail()).toLowerCase();

        requireUsername(username);
        requirePassword(password);
        requireEmail(email);

        // 邮箱注册时只校验邮箱验证码
        // 图形验证码已经在发送邮箱验证码 /api/auth/email-code 时校验过了
        emailCodeStore.verifyAndRemove(email, safe(request.getEmailCode()));

        if (findByUsername(username) != null) {
            throw new RuntimeException("用户名已被注册");
        }

        if (findByEmail(email) != null) {
            throw new RuntimeException("邮箱已被注册");
        }

        LocalDateTime now = LocalDateTime.now();

        UserAccount user = UserAccount.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode(password))
                .email(email)
                .emailVerified(1)
                .status(1)
                .createdTime(now)
                .updatedTime(now)
                .build();

        userMapper.insert(user);

        return buildAuthResponse(user);
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        if (!captchaStore.verifyAndRemove(request.getCaptchaId(), request.getCaptchaCode())) {
            throw new RuntimeException("图形验证码错误或已过期");
        }

        String account = safe(request.getAccount());
        String password = safe(request.getPassword());

        if (account.isBlank() || password.isBlank()) {
            throw new RuntimeException("账号和密码不能为空");
        }

        UserAccount user = findByUsername(account);

        if (user == null) {
            user = findByPhone(account);
        }

        if (user == null) {
            user = findByEmail(account.toLowerCase());
        }

        if (user == null) {
            throw new RuntimeException("账号不存在");
        }

        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new RuntimeException("账号已被禁用");
        }

        if (user.getPasswordHash() == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new RuntimeException("密码错误");
        }

        return buildAuthResponse(user);
    }

    @Override
    public AuthResponse smsLogin(SmsLoginRequest request) {
        String phone = safe(request.getPhone());
        requirePhone(phone);

        smsAuthService.verifyCode(phone, safe(request.getSmsCode()));

        UserAccount user = findByPhone(phone);

        if (user == null) {
            LocalDateTime now = LocalDateTime.now();

            user = UserAccount.builder()
                    .username(generatePhoneUsername(phone))
                    .phone(phone)
                    .status(1)
                    .emailVerified(0)
                    .createdTime(now)
                    .updatedTime(now)
                    .build();

            userMapper.insert(user);
        }

        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new RuntimeException("账号已被禁用");
        }

        return buildAuthResponse(user);
    }

    @Override
    public UserInfoResponse getUserInfo(Long userId) {
        UserAccount user = userMapper.selectById(userId);

        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        return toUserInfo(user);
    }


    @Override
    public WechatMpQrLoginResponse createWechatMpQrLogin() {
        checkWechatMpEnabled();

        String scene = wechatMpLoginSceneStore.createScene(wechatProperties.getSceneExpireMinutes());

        String authUrl = "https://open.weixin.qq.com/connect/oauth2/authorize"
                + "?appid=" + encode(wechatProperties.getAppId())
                + "&redirect_uri=" + encode(wechatProperties.getRedirectUri())
                + "&response_type=code"
                + "&scope=" + encode(wechatProperties.getScope())
                + "&state=" + encode(scene)
                + "#wechat_redirect";

        return WechatMpQrLoginResponse.builder()
                .scene(scene)
                .authUrl(authUrl)
                .expireSeconds(wechatProperties.getSceneExpireMinutes() * 60)
                .build();
    }

    @Override
    public WechatMpPollResponse pollWechatMpLogin(String scene) {
        return wechatMpLoginSceneStore.poll(scene);
    }

    @Override
    public String handleWechatMpCallback(String code, String state) {
        try {
            checkWechatMpEnabled();

            if (state == null || state.isBlank()) {
                throw new RuntimeException("微信登录 state 为空");
            }

            if (!wechatMpLoginSceneStore.existsAndValid(state)) {
                throw new RuntimeException("微信登录二维码已过期，请回到电脑端刷新二维码");
            }

            if (code == null || code.isBlank()) {
                throw new RuntimeException("微信回调 code 为空");
            }

            AuthResponse auth = loginByWechatMpCode(code);

            wechatMpLoginSceneStore.confirm(state, auth);

            return buildWechatCallbackHtml("登录成功", "微信授权成功，请回到电脑页面继续使用 PhotoScore Pro。", true);

        } catch (Exception e) {
            if (state != null && !state.isBlank()) {
                wechatMpLoginSceneStore.fail(state, e.getMessage());
            }

            return buildWechatCallbackHtml("登录失败", e.getMessage(), false);
        }
    }

    private AuthResponse loginByWechatMpCode(String code) {
        JsonNode tokenJson = requestWechatMpToken(code);

        String accessToken = tokenJson.path("access_token").asText();
        String openid = tokenJson.path("openid").asText();
        String unionid = tokenJson.path("unionid").asText(null);

        if (isBlank(openid)) {
            throw new RuntimeException("微信授权失败，未获取到 openid");
        }

        String nickname = "";
        String avatar = "";

        if ("snsapi_userinfo".equalsIgnoreCase(wechatProperties.getScope())) {
            JsonNode userJson = requestWechatMpUserInfo(accessToken, openid);
            nickname = userJson.path("nickname").asText("");
            avatar = userJson.path("headimgurl").asText("");
            if (isBlank(unionid)) {
                unionid = userJson.path("unionid").asText(null);
            }
        }

        UserAccount user = null;

        if (!isBlank(unionid)) {
            user = findByWechatUnionid(unionid);
        }

        if (user == null) {
            user = findByWechatOpenid(openid);
        }

        if (user == null) {
            LocalDateTime now = LocalDateTime.now();

            user = UserAccount.builder()
                    .username(generateWechatUsername(nickname, openid))
                    .wechatOpenid(openid)
                    .wechatUnionid(unionid)
                    .avatarUrl(avatar)
                    .status(1)
                    .emailVerified(0)
                    .createdTime(now)
                    .updatedTime(now)
                    .build();

            userMapper.insert(user);
        } else {
            boolean needUpdate = false;

            if (isBlank(user.getWechatOpenid())) {
                user.setWechatOpenid(openid);
                needUpdate = true;
            }

            if (isBlank(user.getWechatUnionid()) && !isBlank(unionid)) {
                user.setWechatUnionid(unionid);
                needUpdate = true;
            }

            if (!isBlank(avatar)) {
                user.setAvatarUrl(avatar);
                needUpdate = true;
            }

            if (needUpdate) {
                user.setUpdatedTime(LocalDateTime.now());
                userMapper.updateById(user);
            }
        }

        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new RuntimeException("账号已被禁用");
        }

        return buildAuthResponse(user);
    }

    private JsonNode requestWechatMpToken(String code) {
        try {
            String url = "https://api.weixin.qq.com/sns/oauth2/access_token"
                    + "?appid=" + encode(wechatProperties.getAppId())
                    + "&secret=" + encode(wechatProperties.getAppSecret())
                    + "&code=" + encode(code)
                    + "&grant_type=authorization_code";

            String body = httpGet(url);
            JsonNode json = objectMapper.readTree(body);

            if (json.has("errcode")) {
                throw new RuntimeException("微信网页授权 access_token 获取失败: " + body);
            }

            return json;
        } catch (Exception e) {
            throw new RuntimeException("微信网页授权 access_token 获取失败: " + e.getMessage(), e);
        }
    }

    private JsonNode requestWechatMpUserInfo(String accessToken, String openid) {
        try {
            String url = "https://api.weixin.qq.com/sns/userinfo"
                    + "?access_token=" + encode(accessToken)
                    + "&openid=" + encode(openid)
                    + "&lang=zh_CN";

            String body = httpGet(url);
            JsonNode json = objectMapper.readTree(body);

            if (json.has("errcode")) {
                throw new RuntimeException("微信网页授权用户信息获取失败: " + body);
            }

            return json;
        } catch (Exception e) {
            throw new RuntimeException("微信网页授权用户信息获取失败: " + e.getMessage(), e);
        }
    }

    private void checkWechatMpEnabled() {
        if (!wechatProperties.isEnabled()) {
            throw new RuntimeException("微信公众平台登录未启用");
        }

        if (!"mp".equalsIgnoreCase(wechatProperties.getPlatform())) {
            throw new RuntimeException("当前微信登录平台不是 mp，请检查 auth.wechat.platform 配置");
        }

        if (isBlank(wechatProperties.getAppId())
                || isBlank(wechatProperties.getAppSecret())
                || isBlank(wechatProperties.getRedirectUri())) {
            throw new RuntimeException("微信公众平台配置不完整，请检查 AppID、AppSecret、RedirectUri");
        }
    }

    private String buildWechatCallbackHtml(String title, String message, boolean success) {
        String color = success ? "#16a34a" : "#dc2626";
        String safeTitle = escapeHtml(title);
        String safeMessage = escapeHtml(message);

        return """
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>%s</title>
                <style>
                    body {
                        margin: 0;
                        min-height: 100vh;
                        display: grid;
                        place-items: center;
                        background: #0f172a;
                        color: #e5e7eb;
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif;
                    }
                    .card {
                        width: min(420px, calc(100vw - 32px));
                        background: rgba(15, 23, 42, .92);
                        border: 1px solid rgba(148, 163, 184, .24);
                        border-radius: 24px;
                        padding: 28px;
                        text-align: center;
                        box-shadow: 0 28px 80px rgba(0,0,0,.35);
                    }
                    .icon {
                        width: 64px;
                        height: 64px;
                        border-radius: 22px;
                        margin: 0 auto 18px;
                        display: grid;
                        place-items: center;
                        background: %s;
                        color: white;
                        font-size: 32px;
                    }
                    h1 { font-size: 24px; margin: 0 0 10px; }
                    p { color: #94a3b8; line-height: 1.8; margin: 0; }
                </style>
            </head>
            <body>
                <div class="card">
                    <div class="icon">%s</div>
                    <h1>%s</h1>
                    <p>%s</p>
                </div>
            </body>
            </html>
            """.formatted(
                safeTitle,
                color,
                success ? "✓" : "!",
                safeTitle,
                safeMessage
        );
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






    private String httpGet(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }

    private AuthResponse buildAuthResponse(UserAccount user) {
        return AuthResponse.builder()
                .token(jwtUtil.generateToken(user))
                .user(toUserInfo(user))
                .build();
    }

    private UserInfoResponse toUserInfo(UserAccount user) {
        return UserInfoResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .phone(maskPhone(user.getPhone()))
                .email(maskEmail(user.getEmail()))
                .avatarUrl(user.getAvatarUrl())
                .build();
    }

    private UserAccount findByUsername(String username) {
        return userMapper.selectOne(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getUsername, username)
                .last("LIMIT 1"));
    }

    private UserAccount findByPhone(String phone) {
        return userMapper.selectOne(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getPhone, phone)
                .last("LIMIT 1"));
    }

    private UserAccount findByEmail(String email) {
        return userMapper.selectOne(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getEmail, email)
                .last("LIMIT 1"));
    }

    private UserAccount findByWechatOpenid(String openid) {
        return userMapper.selectOne(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getWechatOpenid, openid)
                .last("LIMIT 1"));
    }

    private UserAccount findByWechatUnionid(String unionid) {
        return userMapper.selectOne(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getWechatUnionid, unionid)
                .last("LIMIT 1"));
    }

    private String generatePhoneUsername(String phone) {
        String base = "u" + phone.substring(Math.max(0, phone.length() - 6));
        return uniqueUsername(base);
    }

    private String generateWechatUsername(String nickname, String openid) {
        String clean = safe(nickname).replaceAll("[^a-zA-Z0-9_\\u4e00-\\u9fa5]", "");

        if (clean.length() < 2) {
            clean = "wx" + openid.substring(0, Math.min(8, openid.length()));
        }

        if (clean.length() > 20) {
            clean = clean.substring(0, 20);
        }

        return uniqueUsername(clean);
    }

    private String uniqueUsername(String base) {
        String username = base;
        int index = 1;

        while (findByUsername(username) != null) {
            username = base + "_" + index++;
        }

        return username;
    }

    private void requireUsername(String username) {
        if (username == null || username.length() < 3 || username.length() > 32) {
            throw new RuntimeException("用户名长度必须为3-32位");
        }

        if (!username.matches("^[a-zA-Z0-9_\\u4e00-\\u9fa5]+$")) {
            throw new RuntimeException("用户名只能包含中文、字母、数字和下划线");
        }
    }

    private void requirePassword(String password) {
        if (password == null || password.length() < 6 || password.length() > 32) {
            throw new RuntimeException("密码长度必须为6-32位");
        }
    }

    private void requirePhone(String phone) {
        if (phone == null || !PHONE_PATTERN.matcher(phone).matches()) {
            throw new RuntimeException("手机号格式不正确");
        }
    }

    private void requireEmail(String email) {
        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new RuntimeException("邮箱格式不正确");
        }
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }

        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
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

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private String safe(String s) {
        return s == null ? "" : s.trim();
    }
}