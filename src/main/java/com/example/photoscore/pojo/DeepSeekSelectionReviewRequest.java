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
public class DeepSeekSelectionReviewRequest {

    private Integer topN;

    private List<PhotoItem> photos;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PhotoItem {
        private Long id;
        private String fileName;
        private BigDecimal score;
        private Boolean pass;
        private List<String> comments;
        private List<String> suggestions;
    }
}
