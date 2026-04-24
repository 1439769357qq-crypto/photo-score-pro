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
    private BigDecimal scoreDiff;                     // 总分差值（photo1 - photo2）
    private Map<String, BigDecimal> dimensionDiff;    // 各维度分数差值
    private String advantage;                         // 优势分析文本
}