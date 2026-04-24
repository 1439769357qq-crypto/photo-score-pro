package com.example.photoscore.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PhotoScoreResponse {
    private Long id;
    private String fileName;
    private Long fileSize;
    private String dimension;
    private BigDecimal qualityScore;
    private Boolean isPass;
    private Map<String, BigDecimal> scoreDetails;
    private List<String> scoreReasons;
    private LocalDateTime createdTime;
    private Boolean isDuplicate;
    private String duplicateMessage;
    private List<String> improvementSuggestions;
    private String imagePath;
    private String sceneCategory;
}