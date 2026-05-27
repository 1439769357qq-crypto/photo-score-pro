package com.example.photoscore.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DoubaoVisionReviewResult {

    /**
     * 豆包是否调用成功。
     */
    private boolean success;

    /**
     * 豆包视觉独立参考分，0-100。
     */
    private BigDecimal visionScore;

    /**
     * 豆包建议校准分，0-100。第一阶段只展示，不覆盖现有 qualityScore。
     */
    private BigDecimal finalScoreSuggestion;

    /**
     * 豆包判断置信度，0-1。
     */
    private BigDecimal confidence;

    /**
     * 豆包对本地评分的判断：偏高 / 偏低 / 合理。
     */
    private String scoreAdjustment;

    /**
     * 一句话总结。
     */
    private String summary;

    /**
     * 基于图片内容的专业视觉点评。
     */
    private String visualReview;

    /**
     * 后期修图建议。
     */
    private String retouchSuggestion;

    /**
     * 选片理由。
     */
    private String selectionReason;

    /**
     * 说明本地评分偏高、偏低或合理的原因。
     */
    private String qualityRisk;

    /**
     * 豆包原始响应，方便排查问题。
     */
    private String rawResponse;

    /**
     * 错误信息。
     */
    private String errorMessage;

    public static DoubaoVisionReviewResult disabled() {
        return DoubaoVisionReviewResult.builder()
                .success(false)
                .errorMessage("豆包视觉未启用")
                .build();
    }

    public static DoubaoVisionReviewResult failed(String message) {
        return DoubaoVisionReviewResult.builder()
                .success(false)
                .errorMessage(message)
                .build();
    }
}
