package com.example.photoscore.service;


import com.example.photoscore.pojo.BatchScoreResponse;
import com.example.photoscore.pojo.PhotoScoreResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface PhotoScoreService {
    PhotoScoreResponse scoreSinglePhoto(MultipartFile file, String clientIp, String userAgent);
    BatchScoreResponse scoreBatchPhotos(List<MultipartFile> files, String clientIp, String userAgent);
}