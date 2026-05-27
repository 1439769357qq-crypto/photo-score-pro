package com.example.photoscore.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompareResult {

    private PhotoScoreResponse photo1;
    private PhotoScoreResponse photo2;
    private BigDecimal scoreDiff;
    private Map<String, BigDecimal> dimensionDiff;
    private String advantage;

    private String finalRecommendation;
    private String dimensionDiffTop3;
    private String photo1AdvantageTop3;
    private String photo2AdvantageTop3;

    private String deepSeekCompareConclusion;
    private String deepSeekCompareReason;
    private String deepSeekUsageAdvice;
    private String deepSeekRetouchAdvice;
}