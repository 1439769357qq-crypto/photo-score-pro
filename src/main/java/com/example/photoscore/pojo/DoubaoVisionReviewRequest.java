package com.example.photoscore.pojo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
public class DoubaoVisionReviewRequest {

    /**
     * 当前上传照片文件名。
     */
    private String fileName;

    /**
     * 本地系统识别出的场景分类。
     */
    private String sceneCategory;

    /**
     * 本地系统总分。豆包不会覆盖这个分数，只做参考和校验。
     */
    private BigDecimal localScore;

    /**
     * 本地技术分。
     */
    private BigDecimal technicalScore;

    /**
     * 本地美学分。
     */
    private BigDecimal aestheticScore;

    /**
     * 本地综合分。
     */
    private BigDecimal comprehensiveScore;

    /**
     * 本地各维度评分。
     */
    private Map<String, BigDecimal> scoreDetails;

    /**
     * 本地系统生成的总体评价。
     */
    private String localOverallComment;

    /**
     * 本地系统生成的优先改进建议。
     */
    private String localFinalSuggestion;
}
