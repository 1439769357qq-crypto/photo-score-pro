package com.example.photoscore.service;


import com.example.photoscore.dto.ProPhotoToolkitDtos;
import org.springframework.web.multipart.MultipartFile;

public interface ProPhotoToolkitService {

    ProPhotoToolkitDtos.SingleReport analyzeSingle(MultipartFile file);

    ProPhotoToolkitDtos.BatchCullResponse analyzeBatch(MultipartFile[] files);
}