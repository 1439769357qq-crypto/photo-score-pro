package com.example.photoscore.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompositeScoreResult {
    private BigDecimal totalScore;
    private BigDecimal technicalScore;
    private BigDecimal aestheticScore;
    private BigDecimal comprehensiveScore;
    private BigDecimal nimaAestheticScore;
    private BigDecimal nimaTechnicalScore;
    private List<ScoringResult> scoringResults;
    private List<String> comments;
    private List<String> suggestions;
    private Integer imageWidth;
    private Integer imageHeight;
    private Long elapsedMs;
    private String imagePath;
    private Long id;
    private String sceneCategory;
}
