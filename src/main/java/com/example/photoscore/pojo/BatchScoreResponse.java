package com.example.photoscore.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchScoreResponse {
    private Integer totalCount;
    private Integer validCount;
    private Integer duplicateCount;
    private List<String> duplicateMessages;
    private List<PhotoScoreResponse> scores;
    private Long processTimeMs;
}