package com.example.photoscore.service.impl;

import com.example.photoscore.config.DoubaoVisionProperties;
import com.example.photoscore.pojo.DoubaoVisionReviewRequest;
import com.example.photoscore.pojo.DoubaoVisionReviewResult;
import com.example.photoscore.service.DoubaoVisionAiService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DoubaoVisionAiServiceImpl implements DoubaoVisionAiService {

    private final DoubaoVisionProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public DoubaoVisionReviewResult generateVisionReview(DoubaoVisionReviewRequest request,
                                                         BufferedImage image) {
        if (!properties.isEnabled()) {
            return DoubaoVisionReviewResult.disabled();
        }

        if (!StringUtils.hasText(properties.getApiKey())) {
            return DoubaoVisionReviewResult.failed("DOUBAO_API_KEY 未配置");
        }

        if (!StringUtils.hasText(properties.getModel())) {
            return DoubaoVisionReviewResult.failed("DOUBAO_MODEL 未配置");
        }

        if (image == null) {
            return DoubaoVisionReviewResult.failed("图片为空，无法调用豆包视觉模型");
        }

        try {
            String imageDataUrl = toJpegDataUrl(image, properties.getMaxImageSize(), 0.82f);
            String prompt = buildPrompt(request);

            Map<String, Object> textContent = new LinkedHashMap<>();
            textContent.put("type", "text");
            textContent.put("text", prompt);

            Map<String, Object> imageUrl = new LinkedHashMap<>();
            imageUrl.put("url", imageDataUrl);

            Map<String, Object> imageContent = new LinkedHashMap<>();
            imageContent.put("type", "image_url");
            imageContent.put("image_url", imageUrl);

            List<Map<String, Object>> userContent = new ArrayList<>();
            userContent.add(textContent);
            userContent.add(imageContent);

            Map<String, Object> systemMessage = new LinkedHashMap<>();
            systemMessage.put("role", "system");
            systemMessage.put(
                    "content",
                    "你是一名专业摄影评审、影像编辑和修图指导。你必须只返回 JSON，不要返回 Markdown，不要输出解释性前后缀。"
            );

            Map<String, Object> userMessage = new LinkedHashMap<>();
            userMessage.put("role", "user");
            userMessage.put("content", userContent);

            List<Map<String, Object>> messages = new ArrayList<>();
            messages.add(systemMessage);
            messages.add(userMessage);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", properties.getModel());
            body.put("temperature", properties.getTemperature());
            body.put("max_tokens", properties.getMaxTokens());
            body.put("messages", messages);

            String requestBody = objectMapper.writeValueAsString(body);

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                    .build();

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(properties.getBaseUrl()))
                    .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = client.send(
                    httpRequest,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("豆包调用失败: status={}, body={}",
                        response.statusCode(),
                        response.body()
                );

                return DoubaoVisionReviewResult.failed(
                        "豆包调用失败，HTTP " + response.statusCode()
                );
            }

            String content = extractMessageContent(response.body());
            String cleanJson = cleanJson(content);

            Map<String, Object> resultMap = objectMapper.readValue(
                    cleanJson,
                    new TypeReference<Map<String, Object>>() {
                    }
            );

            return DoubaoVisionReviewResult.builder()
                    .success(true)
                    .visionScore(toBigDecimal(resultMap.get("visionScore")))
                    .finalScoreSuggestion(toBigDecimal(resultMap.get("finalScoreSuggestion")))
                    .confidence(toBigDecimal(resultMap.get("confidence")))
                    .scoreAdjustment(asString(resultMap.get("scoreAdjustment")))
                    .summary(asString(resultMap.get("summary")))
                    .visualReview(asString(resultMap.get("visualReview")))
                    .retouchSuggestion(asString(resultMap.get("retouchSuggestion")))
                    .selectionReason(asString(resultMap.get("selectionReason")))
                    .qualityRisk(asString(resultMap.get("qualityRisk")))
                    .rawResponse(response.body())
                    .build();

        } catch (Exception e) {
            log.warn("豆包视觉评分校验失败，已跳过，不影响原有评分: {}", e.getMessage(), e);
            return DoubaoVisionReviewResult.failed(e.getMessage());
        }
    }

    private String buildPrompt(DoubaoVisionReviewRequest r) {
        return """
            请直接观察图片，并结合照片评分系统的结构化评分数据，完成“视觉评分复核 + 专业摄影评审 + 后期修图建议”。

            你的角色：
            你是一名专业摄影评审、图片编辑和后期指导。你的任务不是简单夸奖或否定照片，而是给出清晰、专业、可执行的评审意见。

            重要要求：
            1. 你可以根据图片内容判断主体、构图、曝光、色彩、清晰度、噪点、影调、空间层次、信息价值和作品完成度。
            2. 请给出你独立判断的 visionScore，范围 0-100。
            3. 请给出 finalScoreSuggestion，范围 0-100，作为视觉复核后的建议校准分。
            4. scoreAdjustment 只能是：偏高、偏低、合理。
            5. confidence 范围 0-1。
            6. 如果你认为本地评分偏高或偏低，请使用“视觉复核认为”“可能偏严”“可能偏宽松”这类审慎表达。
            7. 不要使用“严重失真”“完全错误”“毫无参考价值”等否定本地算法价值的表述。
            8. 本地评分用于量化排序、雷达图和历史记录；你的职责是视觉复核和专业解释，不是取代本地评分系统。
            9. 评语要体现专业摄影语言，不能只写一句泛泛而谈的话。
            10. 后期建议必须具体，可执行，避免只写“提升画质”“优化色彩”这种空话。
            11. 不要输出 Markdown。
            12. 只返回 JSON，不要返回任何解释性前后缀。

            JSON 格式必须是：
            {
              "visionScore": 82.5,
              "finalScoreSuggestion": 78.6,
              "confidence": 0.86,
              "scoreAdjustment": "偏高",
              "summary": "一句话总结，60字以内",
              "visualReview": "专业综合评审，180到260字。需要从主体表达、构图秩序、曝光控制、色彩影调、清晰度、画面完成度、适合用途等角度分析。",
              "retouchSuggestion": "专业后期建议，160到240字。需要给出具体操作方向，例如白平衡、曝光、高光、阴影、对比度、饱和度、锐化、降噪、裁剪、透视修正等。",
              "selectionReason": "选片建议，80到120字。说明适合保留、待修、淘汰，适合作为什么用途。",
              "qualityRisk": "评分差异说明，100到160字。解释本地评分偏高、偏低或合理的原因，语气审慎，不要否定本地算法。"
            }

            评分参考说明：
            - 90-100：专业级优秀作品，主体、构图、光线、色彩和完成度都很强。
            - 80-89：质量较高，适合精选，但仍有少量优化空间。
            - 70-79：基础较好，适合保留或后期修图后使用。
            - 60-69：普通可用，但存在明显短板。
            - 0-59：质量较弱，不建议作为最终精选。

            照片文件名：%s
            场景分类：%s
            本地总分 localScore：%s
            技术分：%s
            美学分：%s
            综合分：%s
            本地维度得分：%s
            本地总体评价：%s
            本地优先建议：%s
            """.formatted(
                safe(r.getFileName()),
                safe(r.getSceneCategory()),
                safe(r.getLocalScore()),
                safe(r.getTechnicalScore()),
                safe(r.getAestheticScore()),
                safe(r.getComprehensiveScore()),
                String.valueOf(r.getScoreDetails()),
                safe(r.getLocalOverallComment()),
                safe(r.getLocalFinalSuggestion())
        );
    }

    @SuppressWarnings("unchecked")
    private String extractMessageContent(String responseBody) throws Exception {
        Map<String, Object> root = objectMapper.readValue(
                responseBody,
                new TypeReference<Map<String, Object>>() {
                }
        );

        Object choicesObj = root.get("choices");
        if (!(choicesObj instanceof List<?> choices) || choices.isEmpty()) {
            throw new IllegalStateException("豆包响应中没有 choices");
        }

        Object firstObj = choices.get(0);
        if (!(firstObj instanceof Map<?, ?> first)) {
            throw new IllegalStateException("豆包 choices[0] 格式异常");
        }

        Object messageObj = first.get("message");
        if (!(messageObj instanceof Map<?, ?> message)) {
            throw new IllegalStateException("豆包响应中没有 message");
        }

        Object contentObj = message.get("content");
        if (contentObj == null) {
            throw new IllegalStateException("豆包响应中没有 message.content");
        }

        return String.valueOf(contentObj);
    }

    private String cleanJson(String content) {
        if (content == null) {
            return "{}";
        }

        String s = content.trim();

        if (s.startsWith("```json")) {
            s = s.substring("```json".length()).trim();
        } else if (s.startsWith("```")) {
            s = s.substring("```".length()).trim();
        }

        if (s.endsWith("```")) {
            s = s.substring(0, s.length() - 3).trim();
        }

        int firstBrace = s.indexOf('{');
        int lastBrace = s.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            s = s.substring(firstBrace, lastBrace + 1);
        }

        return s;
    }

    private String toJpegDataUrl(BufferedImage source,
                                 int maxSize,
                                 float quality) throws Exception {
        BufferedImage resized = resizeForModel(source, maxSize);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            throw new IllegalStateException("当前 JDK 不支持 JPEG Writer");
        }

        ImageWriter writer = writers.next();
        ImageWriteParam param = writer.getDefaultWriteParam();

        if (param.canWriteCompressed()) {
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
        }

        try (MemoryCacheImageOutputStream output = new MemoryCacheImageOutputStream(baos)) {
            writer.setOutput(output);
            writer.write(null, new IIOImage(resized, null, null), param);
        } finally {
            writer.dispose();
        }

        String base64 = Base64.getEncoder().encodeToString(baos.toByteArray());
        return "data:image/jpeg;base64," + base64;
    }

    private BufferedImage resizeForModel(BufferedImage source, int maxSize) {
        int width = source.getWidth();
        int height = source.getHeight();

        int longest = Math.max(width, height);

        if (longest <= maxSize) {
            return toRgbImage(source);
        }

        double scale = maxSize * 1.0 / longest;
        int newWidth = Math.max(1, (int) Math.round(width * scale));
        int newHeight = Math.max(1, (int) Math.round(height * scale));

        BufferedImage target = new BufferedImage(
                newWidth,
                newHeight,
                BufferedImage.TYPE_INT_RGB
        );

        Graphics2D g = target.createGraphics();

        try {
            g.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR
            );
            g.setRenderingHint(
                    RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY
            );
            g.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
            );

            g.setColor(Color.WHITE);
            g.fillRect(0, 0, newWidth, newHeight);
            g.drawImage(source, 0, 0, newWidth, newHeight, null);
        } finally {
            g.dispose();
        }

        return target;
    }

    private BufferedImage toRgbImage(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_INT_RGB) {
            return source;
        }

        BufferedImage target = new BufferedImage(
                source.getWidth(),
                source.getHeight(),
                BufferedImage.TYPE_INT_RGB
        );

        Graphics2D g = target.createGraphics();

        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, source.getWidth(), source.getHeight());
            g.drawImage(source, 0, 0, null);
        } finally {
            g.dispose();
        }

        return target;
    }

    private String asString(Object value) {
        if (value == null) {
            return "";
        }

        return String.valueOf(value).trim();
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }

        try {
            if (value instanceof BigDecimal decimal) {
                return decimal;
            }

            if (value instanceof Number number) {
                return BigDecimal.valueOf(number.doubleValue());
            }

            String s = String.valueOf(value).trim();
            if (s.isEmpty()) {
                return null;
            }

            return new BigDecimal(s);
        } catch (Exception e) {
            log.debug("转换 BigDecimal 失败: value={}", value);
            return null;
        }
    }

    private String safe(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
