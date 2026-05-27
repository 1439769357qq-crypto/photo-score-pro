package com.example.photoscore.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "sms.aliyun")
public class SmsProperties {

    /**
     * 是否启用验证码功能。
     */
    private boolean enabled = true;

    /**
     * fixed      = 固定验证码，不调用阿里云
     * console    = 控制台打印验证码，不调用阿里云
     * short-auth = 阿里云短认证，不需要短信签名
     */
    private String mode = "short-auth";

    /**
     * fixed 模式验证码。
     */
    private String fixedCode = "123456";

    private String accessKeyId;

    private String accessKeySecret;

    /**
     * 阿里云 OpenAPI endpoint。
     * 短认证如果你控制台显示的是其他 endpoint，改环境变量即可。
     */
    private String endpoint = "dypnsapi.aliyuncs.com";

    private String regionId = "cn-shanghai";

    private String apiVersion = "2017-05-25";

    /**
     * 短认证发送验证码 Action。
     */
    private String sendAction = "SendSmsVerifyCode";

    /**
     * 短认证校验验证码 Action。
     */
    private String verifyAction = "VerifySmsCode";

    private int codeExpireMinutes = 5;

    private int resendIntervalSeconds = 60;

    private String signName;      // 对应 sms.aliyun.sign-name
    private String templateCode;  // 对应 sms.aliyun.template-code

    public String getModeSafe() {
        if (mode == null || mode.isBlank()) {
            return "short-auth";
        }
        return mode.trim().toLowerCase();
    }

    public boolean isShortAuthMode() {
        return "short-auth".equals(getModeSafe());
    }

    public boolean isFixedMode() {
        return "fixed".equals(getModeSafe());
    }

    public boolean isConsoleMode() {
        return "console".equals(getModeSafe());
    }
}