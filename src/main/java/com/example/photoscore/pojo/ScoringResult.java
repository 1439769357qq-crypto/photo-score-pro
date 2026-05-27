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
public class ScoringResult {
    private String scorerName;
    private String category;
    private BigDecimal score;
    private Double rawScore;
    private String comment;
    private List<String> suggestions;
    private Long elapsedMs;
}
