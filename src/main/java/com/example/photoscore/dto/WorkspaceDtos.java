package com.example.photoscore.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

public class WorkspaceDtos {
    @Data
    public static class ProjectRequest {
        private String projectName;
        private String clientName;
        private String shootType;
        private LocalDate shootDate;
        private String note;
        private Integer photoCount;
        private BigDecimal avgScore;
        private Integer keepCount;
        private Integer retouchCount;
        private Integer dropCount;
        private Integer selectedCount;
    }

    @Data
    public static class ManualReviewRequest {
        private Long projectId;
        private String photoKey;
        private String fileName;
        private String manualStatus; // RECOMMEND / RETOUCH / DROP / UNSET
        private BigDecimal manualScore;
        private String manualNote;
        private Boolean overrideAi;
        private Boolean selected;
        private String issueTags;
    }

    @Data
    public static class CollectionItemRequest {
        private Long projectId;
        private String photoKey;
        private String fileName;
        private BigDecimal originalScore;
        private BigDecimal finalScore;
        private String finalStatus;
        private String note;
    }

    @Data
    public static class UsageEventRequest {
        private String eventType; // UPLOAD / SCORE / MANUAL_REVIEW / COLLECTION_ADD / EXIF_ANALYZE / PROJECT_SAVE
        private Integer eventCount;
        private Long projectId;
        private String fileName;
        private String metaJson;
    }

    @Data
    @Builder
    public static class UsageSummaryResponse {
        private Integer todayUploadCount;
        private Integer todayScoreCount;
        private Integer todayManualReviewCount;
        private Integer todayCollectionAddCount;
        private Integer todayExifAnalyzeCount;
        private Integer totalUploadCount;
        private Integer totalScoreCount;
        private Integer totalManualReviewCount;
        private Integer totalCollectionCount;
        private Integer totalProjectCount;
        private Integer totalExifCount;
        private String todayQuotaText;
        private LocalDateTime lastActiveAt;
        private Map<String, Integer> todayByType;
        private Map<String, Integer> totalByType;
    }
}
