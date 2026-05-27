package com.example.photoscore.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Data
@Component
@Profile("render")
@ConfigurationProperties(prefix = "mail-api")
public class RenderMailApiProperties {

    /**
     * keplars / console
     */
    private String provider = "keplars";

    /**
     * 发件人名称，例如 PhotoScore Pro
     */
    private String fromName = "PhotoScore Pro";

    /**
     * 发件邮箱。
     * 如果 Keplars 要求验证发件人，这里必须填已验证邮箱。
     */
    private String fromEmail;

    private Keplars keplars = new Keplars();

    @Data
    public static class Keplars {
        private String apiKey;
        private String apiUrl;
        private String authHeaderName = "Authorization";
        private String authHeaderPrefix = "Bearer";
    }
}