package com.example.photoscore.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "auth.email")
public class EmailAuthProperties {

    private int codeExpireMinutes = 5;

    private int resendIntervalSeconds = 60;
}