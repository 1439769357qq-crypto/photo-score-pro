package com.example.photoscore.service;


import com.example.photoscore.config.ProgressManager;
import com.example.photoscore.pojo.BatchScoreResponse;
import com.example.photoscore.pojo.CompareResult;
import com.example.photoscore.pojo.PhotoScoreResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface PhotoScoreService {
    PhotoScoreResponse scoreSinglePhoto(MultipartFile file, String clientIp, String userAgent);
    BatchScoreResponse scoreBatchPhotos(List<MultipartFile> files, String clientIp, String userAgent);
    BatchScoreResponse scoreBatchPhotosWithProgress(List<MultipartFile> files, String clientIp,
                                                    String userAgent, String taskId, ProgressManager progressManager);
    CompareResult comparePhotos(MultipartFile file1, MultipartFile file2, String clientIp, String userAgent);
}