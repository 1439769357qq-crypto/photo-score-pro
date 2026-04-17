package com.example.photoscore.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 单个评分器的评分结果
 * 
 * @author PhotoScore Pro Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScoringResult {

    /**
     * 评分器名称
     */
    private String scorerName;

    /**
     * 评分器类别（TECHNICAL/AESTHETIC/COMPREHENSIVE）
     */
    private String category;

    /**
     * 百分制分数（0-100）
     */
    private BigDecimal score;

    /**
     * 原始分数（0.0-1.0）
     */
    private Double rawScore;

    /**
     * 评分评语
     */
    private String comment;

    /**
     * 改进建议列表
     */
    private List<String> suggestions;

    /**
     * 计算耗时（毫秒）
     */
    private Long elapsedMs;
}