package com.example.photoscore.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeepSeekCompareReviewResult {

    private boolean success;

    private String errorMessage;

    private String compareConclusion;

    private String compareReason;

    private String usageAdvice;

    private String retouchAdvice;
}
