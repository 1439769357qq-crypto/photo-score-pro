package com.example.photoscore.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "doubao.vision")
public class DoubaoVisionProperties {

    /**
     * 是否启用豆包视觉模型。
     * 默认 false，避免未配置好 API Key 时影响现有评分系统。
     */
    private boolean enabled = false;

    /**
     * 火山引擎 Ark API Key。
     */
    private String apiKey;

    /**
     * Ark OpenAI-compatible Chat Completions 地址。
     */
    private String baseUrl = "https://ark.cn-beijing.volces.com/api/v3/chat/completions";

    /**
     * 模型名或 Endpoint ID。
     * 如果火山控制台给的是 endpoint id，填 ep-xxxxxx。
     * 如果支持直接模型名，填 Doubao-Seed-2.0-pro。
     */
    private String model;

    /**
     * HTTP 超时时间，单位：秒。
     */
    private int timeoutSeconds = 60;

    /**
     * 温度参数。评分校验类任务建议低一些，输出更稳定。
     */
    private double temperature = 0.2;

    /**
     * 发送给视觉模型前，图片最长边压缩到这个尺寸。
     */
    private int maxImageSize = 768;

    /**
     * 模型最大输出 token。
     */
    private int maxTokens = 900;
}
