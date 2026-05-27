package com.example.photoscore.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "deepseek")
public class DeepSeekProperties {

    /**
     * 是否启用 DeepSeek 文本报告。
     */
    private boolean enabled = false;

    /**
     * DeepSeek API Key。不要写死到代码里，使用环境变量 DEEPSEEK_API_KEY。
     */
    private String apiKey;

    /**
     * DeepSeek Chat Completions 完整接口地址。
     * 常用值：https://api.deepseek.com/chat/completions
     */
    private String baseUrl = "https://api.deepseek.com/chat/completions";

    /**
     * 模型名。以你的 DeepSeek 控制台为准。
     * 常用值：deepseek-chat
     */
    private String model = "deepseek-v4-pro";

    private int timeoutSeconds = 45;

    private int maxTokens = 1200;

    private double temperature = 0.25;
}
