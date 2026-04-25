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
}
