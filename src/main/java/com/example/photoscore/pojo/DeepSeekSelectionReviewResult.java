package com.example.photoscore.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeepSeekSelectionReviewResult {

    private boolean success;

    private String errorMessage;

    private String selectionSummary;

    private String selectionStrategy;

    private String keepAdvice;

    private String eliminateAdvice;

    private String retouchAdvice;
}
