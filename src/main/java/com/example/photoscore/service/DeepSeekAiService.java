package com.example.photoscore.service;

import com.example.photoscore.pojo.DeepSeekCompareReviewRequest;
import com.example.photoscore.pojo.DeepSeekCompareReviewResult;
import com.example.photoscore.pojo.DeepSeekPhotoReviewRequest;
import com.example.photoscore.pojo.DeepSeekPhotoReviewResult;
import com.example.photoscore.pojo.DeepSeekSelectionReviewRequest;
import com.example.photoscore.pojo.DeepSeekSelectionReviewResult;

public interface DeepSeekAiService {

    boolean isEnabled();

    DeepSeekPhotoReviewResult generatePhotoReview(DeepSeekPhotoReviewRequest request);

    DeepSeekSelectionReviewResult generateSelectionReview(DeepSeekSelectionReviewRequest request);

    DeepSeekCompareReviewResult generateCompareReview(DeepSeekCompareReviewRequest request);
}
