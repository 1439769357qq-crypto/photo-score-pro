package com.example.photoscore.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

public class ProPhotoToolkitDtos {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SingleReport {
        private String fileName;
        private Long fileSize;
        private Integer width;
        private Integer height;
        private String dimension;
        private Double megapixels;
        private String orientation;
        private String aspectRatio;

        private Double technicalScore;
        private String grade;
        private String professionalConclusion;

        private ImageMetrics metrics;
        private List<String> strengths;
        private List<String> risks;
        private List<String> useAdvice;
        private RetouchPreset retouchPreset;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImageMetrics {
        private Double brightness;
        private Double exposureQuality;
        private Double contrast;
        private Double dynamicRange;
        private Double sharpness;
        private Double noiseControl;
        private Double saturation;
        private Double colorCast;
        private String colorTemperature;
        private String toneStyle;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RetouchPreset {
        private String exposure;
        private String contrast;
        private String highlights;
        private String shadows;
        private String clarity;
        private String texture;
        private String dehaze;
        private String vibrance;
        private String noiseReduction;
        private String sharpen;
        private String whiteBalance;
        private String summary;
        private Map<String, String> parameters;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchCullResponse {
        private Integer totalCount;
        private Integer analyzedCount;
        private Integer excellentCount;
        private Integer usableCount;
        private Integer retouchCount;
        private Integer eliminateCount;

        private String batchConclusion;
        private List<BatchItem> items;
        private List<DuplicatePair> duplicatePairs;
        private List<String> deliveryAdvice;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchItem {
        private Integer rank;
        private String fileName;
        private Integer width;
        private Integer height;
        private String dimension;
        private Double technicalScore;
        private String grade;
        private String group;
        private String summary;
        private List<String> risks;
        private String keepAdvice;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DuplicatePair {
        private String fileA;
        private String fileB;
        private Integer hammingDistance;
        private Double similarity;
        private String reason;
    }
}