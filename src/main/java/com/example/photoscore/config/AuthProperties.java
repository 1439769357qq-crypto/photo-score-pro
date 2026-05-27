package com.example.photoscore.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "auth.jwt")
public class AuthProperties {
    private String secret = "PhotoScorePro_2026_Login_JWT_Secret_9xKp72LmQw38VzTn";
    private int expireHours = 48;
}
