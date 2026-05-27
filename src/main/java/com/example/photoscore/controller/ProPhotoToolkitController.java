package com.example.photoscore.controller;

import com.example.photoscore.dto.ProApiResponse;
import com.example.photoscore.dto.ProPhotoToolkitDtos;
import com.example.photoscore.service.ProPhotoToolkitService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/pro-tools")
public class ProPhotoToolkitController {

    private static final int MAX_BATCH_SIZE = 40;

    private final ProPhotoToolkitService proPhotoToolkitService;

    @GetMapping("/health")
    public ProApiResponse<String> health() {
        return ProApiResponse.success("pro-tools ready");
    }

    @PostMapping(value = "/single-diagnosis", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProApiResponse<ProPhotoToolkitDtos.SingleReport> singleDiagnosis(@RequestPart("file") MultipartFile file) {
        try {
            if (file == null || file.isEmpty()) {
                return ProApiResponse.badRequest("Please upload one photo.");
            }

            return ProApiResponse.success(proPhotoToolkitService.analyzeSingle(file));
        } catch (IllegalArgumentException e) {
            return ProApiResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            return ProApiResponse.fail(e.getMessage());
        }
    }

    @PostMapping(value = "/batch-cull", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProApiResponse<ProPhotoToolkitDtos.BatchCullResponse> batchCull(@RequestPart("files") MultipartFile[] files) {
        try {
            if (files == null || files.length == 0) {
                return ProApiResponse.badRequest("Please upload at least one photo.");
            }

            if (files.length > MAX_BATCH_SIZE) {
                return ProApiResponse.badRequest("A single batch can analyze at most " + MAX_BATCH_SIZE + " photos.");
            }

            return ProApiResponse.success(proPhotoToolkitService.analyzeBatch(files));
        } catch (IllegalArgumentException e) {
            return ProApiResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            return ProApiResponse.fail(e.getMessage());
        }
    }
}
