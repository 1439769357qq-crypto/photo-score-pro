package com.example.photoscore.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeepSeekCompareReviewRequest {

    private PhotoItem photo1;

    private PhotoItem photo2;

    private String localConclusion;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PhotoItem {
        private String label;
        private String fileName;
        private BigDecimal score;
        private Map<String, BigDecimal> scoreDetails;
        private List<String> comments;
        private List<String> suggestions;
    }
}
