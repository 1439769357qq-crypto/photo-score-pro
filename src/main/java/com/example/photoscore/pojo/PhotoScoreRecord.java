package com.example.photoscore.pojo;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("photo_score_record")
public class PhotoScoreRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String fileName;
    private String fileHash;
    private Long fileSize;
    private Integer width;
    private Integer height;
    private String format;
    private BigDecimal totalScore;
    private Integer isPass;
    private BigDecimal technicalScore;
    private BigDecimal clarityScore;
    private BigDecimal noiseScore;
    private BigDecimal exposureScore;
    private BigDecimal colorAccuracyScore;
    private BigDecimal toneScore;
    private BigDecimal resolutionScore;
    private BigDecimal aestheticScore;
    private BigDecimal compositionScore;
    private BigDecimal lightingScore;
    private BigDecimal themeScore;
    private BigDecimal momentScore;
    private BigDecimal comprehensiveScore;
    private BigDecimal impactScore;
    private BigDecimal styleScore;
    private BigDecimal difficultyScore;
    private BigDecimal socialValueScore;
    private BigDecimal nimaTechnicalScore;
    private BigDecimal nimaAestheticScore;
    private String scoreReason;
    private String improvementSuggestions;
    private String clientIp;
    private String userAgent;
    private Integer processTimeMs;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
}