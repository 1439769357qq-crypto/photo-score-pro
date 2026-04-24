package com.example.photoscore.controller;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.photoscore.config.ProgressManager;
import com.example.photoscore.mapper.PhotoScoreRecordMapper;
import com.example.photoscore.pojo.*;
import com.example.photoscore.service.PhotoScoreService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping("/photo-score")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class PhotoScoreController {

    private final PhotoScoreService photoScoreService;


    private final PhotoScoreRecordMapper recordMapper;


    private final ProgressManager progressManager;

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
    @GetMapping("/history")
    public Result<Map<String, Object>> getHistory(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String startDate,  // 格式 yyyy-MM-dd
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Integer minScore,
            @RequestParam(required = false) Integer maxScore) {

        Page<PhotoScoreRecord> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<PhotoScoreRecord> wrapper = new LambdaQueryWrapper<>();

        // 时间范围筛选
        if (startDate != null && !startDate.isEmpty()) {
            wrapper.ge(PhotoScoreRecord::getCreatedTime, LocalDate.parse(startDate).atStartOfDay());
        }
        if (endDate != null && !endDate.isEmpty()) {
            wrapper.le(PhotoScoreRecord::getCreatedTime, LocalDate.parse(endDate).atTime(23, 59, 59));
        }
        // 分数范围筛选
        if (minScore != null) {
            wrapper.ge(PhotoScoreRecord::getTotalScore, BigDecimal.valueOf(minScore));
        }
        if (maxScore != null) {
            wrapper.le(PhotoScoreRecord::getTotalScore, BigDecimal.valueOf(maxScore));
        }

        wrapper.orderByDesc(PhotoScoreRecord::getCreatedTime);
        Page<PhotoScoreRecord> resultPage = recordMapper.selectPage(pageParam, wrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("total", resultPage.getTotal());
        result.put("pages", resultPage.getPages());
        result.put("current", resultPage.getCurrent());
        result.put("records", resultPage.getRecords());

        return Result.success(result);
    }
    @PostMapping("/batch/async")
    public Result<String> scoreBatchAsync(@RequestParam("files") List<MultipartFile> files,
                                          HttpServletRequest request) {
        String taskId = UUID.randomUUID().toString();
        String clientIp = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");

        progressManager.initTask(taskId, files.size());

        // 异步处理
        CompletableFuture.runAsync(() -> {
            try {
                BatchScoreResponse response = photoScoreService.scoreBatchPhotosWithProgress(
                        files, clientIp, userAgent, taskId, progressManager);
                progressManager.completeTask(taskId, response);
            } catch (Exception e) {
                log.error("异步批量评分失败", e);
                progressManager.completeTask(taskId, Result.error("评分失败: " + e.getMessage()));
            }
        });

        return Result.success(taskId);
    }

    @GetMapping("/batch/progress/{taskId}")
    public Result<ProgressManager.ProgressInfo> getBatchProgress(@PathVariable String taskId) {
        ProgressManager.ProgressInfo info = progressManager.getProgress(taskId);
        if (info == null) {
            return Result.error("任务不存在或已过期");
        }
        return Result.success(info);
    }
    @PostMapping("/compare")
    public Result<CompareResult> comparePhotos(@RequestParam("file1") MultipartFile file1,
                                               @RequestParam("file2") MultipartFile file2,
                                               HttpServletRequest request) {
        String clientIp = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        try {
            CompareResult result = photoScoreService.comparePhotos(file1, file2, clientIp, userAgent);
            return Result.success(result);
        } catch (Exception e) {
            log.error("照片对比失败", e);
            return Result.error("对比失败: " + e.getMessage());
        }
    }
}