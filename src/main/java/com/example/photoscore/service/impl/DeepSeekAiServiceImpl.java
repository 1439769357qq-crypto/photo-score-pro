package com.example.photoscore.service.impl;

import com.example.photoscore.config.DeepSeekProperties;
import com.example.photoscore.pojo.DeepSeekCompareReviewRequest;
import com.example.photoscore.pojo.DeepSeekCompareReviewResult;
import com.example.photoscore.pojo.DeepSeekPhotoReviewRequest;
import com.example.photoscore.pojo.DeepSeekPhotoReviewResult;
import com.example.photoscore.pojo.DeepSeekSelectionReviewRequest;
import com.example.photoscore.pojo.DeepSeekSelectionReviewResult;
import com.example.photoscore.service.DeepSeekAiService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeepSeekAiServiceImpl implements DeepSeekAiService {

    private final DeepSeekProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public boolean isEnabled() {
        return properties.isEnabled()
                && properties.getApiKey() != null
                && !properties.getApiKey().isBlank()
                && properties.getModel() != null
                && !properties.getModel().isBlank();
    }

    @Override
    public DeepSeekPhotoReviewResult generatePhotoReview(DeepSeekPhotoReviewRequest request) {
        if (!isEnabled()) {
            return DeepSeekPhotoReviewResult.builder()
                    .success(false)
                    .errorMessage("DeepSeek 未启用或 API Key / Model 未配置")
                    .build();
        }

        try {
            String content = postChat(systemPrompt(), buildPhotoPrompt(request), properties.getMaxTokens());
            String json = extractJson(content);
            DeepSeekPhotoReviewResult result = objectMapper.readValue(json, DeepSeekPhotoReviewResult.class);
            result.setSuccess(true);
            return result;
        } catch (Exception e) {
            log.warn("DeepSeek 单张照片专业报告生成失败: {}", e.getMessage(), e);
            return DeepSeekPhotoReviewResult.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    @Override
    public DeepSeekSelectionReviewResult generateSelectionReview(DeepSeekSelectionReviewRequest request) {
        if (!isEnabled()) {
            return DeepSeekSelectionReviewResult.builder()
                    .success(false)
                    .errorMessage("DeepSeek 未启用或 API Key / Model 未配置")
                    .build();
        }

        try {
            String content = postChat(systemPrompt(), buildSelectionPrompt(request), properties.getMaxTokens());
            String json = extractJson(content);
            DeepSeekSelectionReviewResult result = objectMapper.readValue(json, DeepSeekSelectionReviewResult.class);
            result.setSuccess(true);
            return result;
        } catch (Exception e) {
            log.warn("DeepSeek 智能选片报告生成失败: {}", e.getMessage(), e);
            return DeepSeekSelectionReviewResult.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    @Override
    public DeepSeekCompareReviewResult generateCompareReview(DeepSeekCompareReviewRequest request) {
        if (!isEnabled()) {
            return DeepSeekCompareReviewResult.builder()
                    .success(false)
                    .errorMessage("DeepSeek 未启用或 API Key / Model 未配置")
                    .build();
        }

        try {
            String content = postChat(systemPrompt(), buildComparePrompt(request), properties.getMaxTokens());
            String json = extractJson(content);
            DeepSeekCompareReviewResult result = objectMapper.readValue(json, DeepSeekCompareReviewResult.class);
            result.setSuccess(true);
            return result;
        } catch (Exception e) {
            log.warn("DeepSeek 照片对比报告生成失败: {}", e.getMessage(), e);
            return DeepSeekCompareReviewResult.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    private String postChat(String systemPrompt, String userPrompt, int maxTokens) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getModel());
        body.put("temperature", properties.getTemperature());
        body.put("max_tokens", maxTokens);

        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ));

        String requestJson = objectMapper.writeValueAsString(body);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(properties.getBaseUrl()))
                .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + properties.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException("DeepSeek HTTP " + response.statusCode() + ": " + response.body());
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode contentNode = root.path("choices").path(0).path("message").path("content");

        if (contentNode.isMissingNode() || contentNode.asText().isBlank()) {
            throw new RuntimeException("DeepSeek 返回内容为空: " + response.body());
        }

        return contentNode.asText();
    }

    private String extractJson(String content) {
        if (content == null) {
            throw new IllegalArgumentException("DeepSeek content 为空");
        }

        String text = content.trim();

        if (text.startsWith("```")) {
            text = text.replaceFirst("^```json", "")
                    .replaceFirst("^```", "")
                    .replaceFirst("```$", "")
                    .trim();
        }

        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');

        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("DeepSeek 未返回 JSON: " + content);
        }

        return text.substring(start, end + 1);
    }

    private String systemPrompt() {
        return """
                你是一名专业摄影评审、图片编辑、选片师和后期指导。
                你不能直接看图片，只能基于系统提供的本地评分、视觉模型评语和维度数据进行专业文字分析。
                你的输出必须客观、专业、可执行，避免空话。
                只返回 JSON，不要返回 Markdown，不要返回解释性前后缀。
                """;
    }

    private String buildPhotoPrompt(DeepSeekPhotoReviewRequest r) throws Exception {
        return """
                请基于以下照片评分数据，生成专业摄影报告。

                要求：
                1. 不要重复堆砌评分器名称。
                2. 要像专业摄影评审一样分析：主体表达、构图、曝光、色彩、清晰度、画面完成度、适合用途。
                3. 修图建议必须具体，可执行。
                4. 只返回 JSON。

                JSON 格式：
                {
                  "summary": "一句话总结，60字以内",
                  "professionalReview": "专业综合评审，180到260字",
                  "strengths": "优点分析，80到140字",
                  "weaknesses": "问题分析，80到140字",
                  "retouchPlan": "后期修图方案，160到240字",
                  "usageAdvice": "适合用途，80到140字",
                  "keepDecision": "保留/待修/淘汰建议，60到100字"
                }

                照片数据：
                %s
                """.formatted(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(r));
    }

    private String buildSelectionPrompt(DeepSeekSelectionReviewRequest r) throws Exception {
        return """
                请基于一组照片的评分结果，生成智能选片报告。

                要求：
                1. 说明应该保留哪些照片、哪些作为备选、哪些建议淘汰。
                2. 不要只看分数，也要结合评语、用途、重复度、可修复性。
                3. 只返回 JSON。

                JSON 格式：
                {
                  "selectionSummary": "整体选片结论，120到200字",
                  "selectionStrategy": "选片策略说明，120到200字",
                  "keepAdvice": "推荐保留建议，120到220字",
                  "eliminateAdvice": "备选/淘汰建议，120到220字",
                  "retouchAdvice": "后期处理建议，120到220字"
                }

                选片数据：
                %s
                """.formatted(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(r));
    }

    private String buildComparePrompt(DeepSeekCompareReviewRequest r) throws Exception {
        return """
                请基于两张照片的评分结果，生成专业对比分析。

                要求：
                1. 明确推荐选择哪一张，或者说明两张用途不同。
                2. 从主体、构图、光线、色彩、清晰度、用途等角度分析。
                3. 只返回 JSON。

                JSON 格式：
                {
                  "compareConclusion": "最终推荐结论，60到120字",
                  "compareReason": "详细推荐理由，160到260字",
                  "usageAdvice": "两张照片分别适合的用途，100到180字",
                  "retouchAdvice": "如果要修图，分别如何处理，100到180字"
                }

                对比数据：
                %s
                """.formatted(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(r));
    }
}
