package com.example.photoscore.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeepSeekPhotoReviewRequest {

    private Long recordId;

    private String fileName;

    private String sceneCategory;

    /**
     * 最终综合分，0-100。
     */
    private BigDecimal finalScore;

    /**
     * 本地量化分，0-100。没有就可为空。
     */
    private BigDecimal localScore;

    private BigDecimal technicalScore;

    private BigDecimal aestheticScore;

    private BigDecimal comprehensiveScore;

    private Map<String, BigDecimal> scoreDetails;

    /**
     * 本地 / 视觉模型已有评语。
     */
    private List<String> existingComments;

    /**
     * 本地 / 视觉模型已有建议。
     */
    private List<String> existingSuggestions;
}
