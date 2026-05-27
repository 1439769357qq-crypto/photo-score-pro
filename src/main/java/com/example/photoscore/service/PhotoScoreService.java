package com.example.photoscore.service;


import com.example.photoscore.config.ProgressManager;
import com.example.photoscore.pojo.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

public interface PhotoScoreService {
    PhotoScoreResponse scoreSinglePhoto(MultipartFile file, String clientIp, String userAgent);
    BatchScoreResponse scoreBatchPhotos(List<MultipartFile> files, String clientIp, String userAgent);
    BatchScoreResponse scoreBatchPhotosWithProgress(List<CachedMultipartFile> files, String clientIp,
                                                    String userAgent, String taskId, ProgressManager progressManager,String mode,
                                                    Integer topN);
    CompareResult comparePhotos(MultipartFile file1, MultipartFile file2, String clientIp, String userAgent) throws IOException;
    void downloadPhotosAsZip(List<PhotoScoreRecord> records, OutputStream outputStream) throws IOException;
}