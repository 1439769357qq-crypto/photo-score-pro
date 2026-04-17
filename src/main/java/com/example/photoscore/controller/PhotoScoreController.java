package com.example.photoscore.controller;


import com.example.photoscore.pojo.BatchScoreResponse;
import com.example.photoscore.pojo.PhotoScoreResponse;
import com.example.photoscore.pojo.Result;
import com.example.photoscore.service.PhotoScoreService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/photo-score")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class PhotoScoreController {

    private final PhotoScoreService photoScoreService;

    @PostMapping("/single")
    public Result<PhotoScoreResponse> scoreSingle(@RequestParam("file") MultipartFile file,
                                                  HttpServletRequest request) {
        if (file == null || file.isEmpty()) {
            return Result.badRequest("请上传照片文件");
        }
        String clientIp = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        try {
            PhotoScoreResponse response = photoScoreService.scoreSinglePhoto(file, clientIp, userAgent);
            return Result.success(response);
        } catch (Exception e) {
            log.error("单张评分失败: {}", e.getMessage(), e);
            return Result.error("评分失败: " + e.getMessage());
        }
    }

    @PostMapping("/batch")
    public Result<BatchScoreResponse> scoreBatch(@RequestParam("files") List<MultipartFile> files,
                                                 HttpServletRequest request) {
        log.info("批量评分请求: {} 张照片", files.size());
        String clientIp = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        try {
            BatchScoreResponse response = photoScoreService.scoreBatchPhotos(files, clientIp, userAgent);
            return Result.success(response);
        } catch (Exception e) {
            log.error("批量评分失败: {}", e.getMessage(), e);
            return Result.error("批量评分失败: " + e.getMessage());
        }
    }

    @GetMapping("/health")
    public Result<String> health() {
        return Result.success("Photo Score Pro is running");
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

}