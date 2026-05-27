package com.example.photoscore.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "auth.wechat")
public class WechatLoginProperties {

    private boolean enabled = false;

    private String platform = "mp";

    private String appId;

    private String appSecret;

    private String redirectUri = "http://localhost:8080/api/auth/wechat/mp/callback";

    private String scope = "snsapi_userinfo";

    private String frontendLoginUrl = "http://localhost:8080/login.html";

    private int sceneExpireMinutes = 5;

    /**
     * 微信公众平台接口配置 Token。
     */
    private String verifyToken = "PhotoScoreWechatToken2026";
}