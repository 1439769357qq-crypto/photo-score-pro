package com.example.photoscore.service.impl;

import com.aliyuncs.CommonRequest;
import com.aliyuncs.CommonResponse;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.http.MethodType;
import com.aliyuncs.profile.DefaultProfile;
import com.example.photoscore.config.SmsProperties;
import com.example.photoscore.service.SmsAuthService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class SmsAuthServiceImpl implements SmsAuthService {

    private final SmsProperties properties;
    private final ObjectMapper objectMapper;

    private final SecureRandom random = new SecureRandom();

    /**
     * fixed / console 模式使用。
     * short-auth 模式由阿里云负责保存和校验验证码。
     */
    private final Map<String, LocalSmsRecord> localCodeCache = new ConcurrentHashMap<>();
    private final Map<String, Long> phoneNextSendTime = new ConcurrentHashMap<>();
    private final Map<String, Long> ipNextSendTime = new ConcurrentHashMap<>();

    @Override
    public void sendCode(String phone, String ip) {
        if (!properties.isEnabled()) {
            throw new RuntimeException("短信认证功能未启用");
        }

        checkFrequency(phone, ip);

        if (properties.isFixedMode()) {
            String code = properties.getFixedCode();
            saveLocalCode(phone, code);
            log.warn("【固定验证码模式】手机号={}，验证码={}，不会真实发送短信。", maskPhone(phone), code);
            return;
        }

        if (properties.isConsoleMode()) {
            String code = randomCode();
            saveLocalCode(phone, code);
            log.warn("【控制台验证码模式】手机号={}，验证码={}，不会真实发送短信。", maskPhone(phone), code);
            return;
        }

        if (properties.isShortAuthMode()) {
            sendByAliyunShortAuth(phone);
            markSendFrequency(phone, ip);
            return;
        }

        throw new RuntimeException("未知短信认证模式 SMS_MODE=" + properties.getModeSafe());
    }

    @Override
    public void verifyCode(String phone, String code) {
        if (properties.isFixedMode() || properties.isConsoleMode()) {
            verifyLocalCode(phone, code);
            return;
        }

        if (properties.isShortAuthMode()) {
            verifyByAliyunShortAuth(phone, code);
            return;
        }

        throw new RuntimeException("未知短信认证模式 SMS_MODE=" + properties.getModeSafe());
    }

    private void sendByAliyunShortAuth(String phone) {
        requireAliyunConfig();

        try {
            CommonRequest request = baseRequest(properties.getSendAction());

            request.putQueryParameter("PhoneNumber", phone);
            // ★ 下面两行是关键修复，必须添加
            request.putQueryParameter("SignName", properties.getSignName());           // 赠送签名名称
            request.putQueryParameter("TemplateCode", properties.getTemplateCode());   // 赠送模板CODE
            // 模板变量可以留空或传空对象
            request.putQueryParameter("TemplateParam", "{}");

            CommonResponse response = client().getCommonResponse(request);
            JsonNode root = objectMapper.readTree(response.getData());

            if (!isSuccess(root)) {
                throw new RuntimeException(extractError(root, response.getData()));
            }

            log.info("阿里云短认证验证码发送成功: phone={}", maskPhone(phone));
        } catch (Exception e) {
            throw new RuntimeException("阿里云短认证发送验证码失败: " + e.getMessage(), e);
        }
    }

    private void verifyByAliyunShortAuth(String phone, String code) {
        requireAliyunConfig();

        if (code == null || code.isBlank()) {
            throw new RuntimeException("验证码不能为空");
        }

        try {
            CommonRequest request = baseRequest(properties.getVerifyAction());

            /**
             * 这里使用短认证校验参数。
             * 如果你控制台参数名不是 PhoneNumber / VerifyCode，
             * 把控制台截图或参数名发我，我给你改对应版本。
             */
            request.putQueryParameter("PhoneNumber", phone);
            request.putQueryParameter("SmsCode", code.trim());

            CommonResponse response = client().getCommonResponse(request);
            JsonNode root = objectMapper.readTree(response.getData());

            if (!isSuccess(root)) {
                throw new RuntimeException(extractError(root, response.getData()));
            }

            log.info("阿里云短认证验证码校验成功: phone={}", maskPhone(phone));

        } catch (Exception e) {
            throw new RuntimeException("验证码校验失败: " + e.getMessage(), e);
        }
    }

    private CommonRequest baseRequest(String action) {
        CommonRequest request = new CommonRequest();
        request.setSysMethod(MethodType.POST);
        request.setSysDomain(properties.getEndpoint());
        request.setSysVersion(properties.getApiVersion());
        request.setSysAction(action);
        return request;
    }

    private IAcsClient client() {
        DefaultProfile profile = DefaultProfile.getProfile(
                properties.getRegionId(),
                properties.getAccessKeyId(),
                properties.getAccessKeySecret()
        );
        return new DefaultAcsClient(profile);
    }

    private void requireAliyunConfig() {
        if (isBlank(properties.getAccessKeyId())
                || isBlank(properties.getAccessKeySecret())
                || isBlank(properties.getEndpoint())
                || isBlank(properties.getApiVersion())
                || isBlank(properties.getSendAction())
                || isBlank(properties.getVerifyAction())) {
            throw new RuntimeException("阿里云短认证配置不完整，请检查 AccessKey、Endpoint、ApiVersion、Action 配置");
        }
    }

    private boolean isSuccess(JsonNode root) {
        String code = root.path("Code").asText();

        if ("OK".equalsIgnoreCase(code)) {
            return true;
        }

        if ("Success".equalsIgnoreCase(code)) {
            return true;
        }

        boolean success = root.path("Success").asBoolean(false);
        if (success) {
            return true;
        }

        String successText = root.path("Success").asText("");
        return "true".equalsIgnoreCase(successText);
    }

    private String extractError(JsonNode root, String raw) {
        String code = root.path("Code").asText("");
        String message = root.path("Message").asText("");

        if (!code.isBlank() || !message.isBlank()) {
            return "Code=" + code + ", Message=" + message;
        }

        return raw;
    }

    private void checkFrequency(String phone, String ip) {
        long now = System.currentTimeMillis();

        Long phoneNext = phoneNextSendTime.get(phone);
        if (phoneNext != null && now < phoneNext) {
            long seconds = Math.max(1, (phoneNext - now) / 1000);
            throw new RuntimeException("验证码发送太频繁，请 " + seconds + " 秒后再试");
        }

        Long ipNext = ipNextSendTime.get(ip);
        if (ipNext != null && now < ipNext) {
            long seconds = Math.max(1, (ipNext - now) / 1000);
            throw new RuntimeException("当前IP发送太频繁，请 " + seconds + " 秒后再试");
        }
    }

    private void markSendFrequency(String phone, String ip) {
        long nextSendAt = Instant.now()
                .plusSeconds(properties.getResendIntervalSeconds())
                .toEpochMilli();

        phoneNextSendTime.put(phone, nextSendAt);

        if (ip != null) {
            ipNextSendTime.put(ip, nextSendAt);
        }
    }

    private void saveLocalCode(String phone, String code) {
        long expireAt = Instant.now()
                .plusSeconds(properties.getCodeExpireMinutes() * 60L)
                .toEpochMilli();

        long nextSendAt = Instant.now()
                .plusSeconds(properties.getResendIntervalSeconds())
                .toEpochMilli();

        localCodeCache.put(phone, new LocalSmsRecord(code, expireAt, 0));
        phoneNextSendTime.put(phone, nextSendAt);
    }

    private void verifyLocalCode(String phone, String code) {
        LocalSmsRecord record = localCodeCache.get(phone);

        if (record == null) {
            throw new RuntimeException("验证码不存在或已过期");
        }

        if (System.currentTimeMillis() > record.expireAt) {
            localCodeCache.remove(phone);
            throw new RuntimeException("验证码已过期");
        }

        if (record.failCount >= 5) {
            localCodeCache.remove(phone);
            throw new RuntimeException("验证码错误次数过多，请重新获取");
        }

        if (code == null || !record.code.equals(code.trim())) {
            localCodeCache.put(phone, new LocalSmsRecord(record.code, record.expireAt, record.failCount + 1));
            throw new RuntimeException("验证码错误");
        }

        localCodeCache.remove(phone);
    }

    private String randomCode() {
        return String.format("%06d", random.nextInt(1_000_000));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }

        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private record LocalSmsRecord(String code, long expireAt, int failCount) {
    }
}