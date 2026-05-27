package com.example.photoscore.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeepSeekPhotoReviewResult {

    private boolean success;

    private String errorMessage;

    /**
     * 一句话专业结论。
     */
    private String summary;

    /**
     * 专业综合评审。
     */
    private String professionalReview;

    /**
     * 优点分析。
     */
    private String strengths;

    /**
     * 问题分析。
     */
    private String weaknesses;

    /**
     * 专业修图方案。
     */
    private String retouchPlan;

    /**
     * 适合用途。
     */
    private String usageAdvice;

    /**
     * 保留/待修/淘汰建议。
     */
    private String keepDecision;
}
