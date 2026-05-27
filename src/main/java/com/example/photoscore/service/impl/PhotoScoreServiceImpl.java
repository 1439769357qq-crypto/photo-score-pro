package com.example.photoscore.service.impl;


import com.example.photoscore.config.ProgressManager;
import com.example.photoscore.mapper.PhotoScoreRecordMapper;
import com.example.photoscore.pojo.*;
import com.example.photoscore.service.DeepSeekAiService;
import com.example.photoscore.service.DoubaoVisionAiService;
import com.example.photoscore.service.PhotoScoreService;
import com.example.photoscore.service.SceneClassifier;
import com.example.photoscore.security.UserContext;
import com.example.photoscore.util.ImageHashUtil;
import com.example.photoscore.util.OpenCVUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class PhotoScoreServiceImpl implements PhotoScoreService {

    private final PhotoScoreRecordMapper recordMapper;
    private final NIMAScorer nimaScorer;
    private final ObjectMapper objectMapper;
    private final DoubaoVisionAiService doubaoVisionAiService;
    // 评分器注入
    private final ClarityScorer clarityScorer;
    private final NoiseScorer noiseScorer;
    private final ExposureScorer exposureScorer;
    private final ColorAccuracyScorer colorAccuracyScorer;
    private final ToneScorer toneScorer;
    private final ResolutionScorer resolutionScorer;
    private final CompositionScorer compositionScorer;
    private final LightingScorer lightingScorer;
    private final ThemeScorer themeScorer;
    private final MomentScorer momentScorer;
    private final ImpactScorer impactScorer;
    private final StyleScorer styleScorer;
    private final DifficultyScorer difficultyScorer;
    private final SocialValueScorer socialValueScorer;
    private final DeepSeekAiService deepSeekAiService;
    private List<BaseScorer> scorers;
    @Qualifier("asyncExecutor")  // 指定使用我们配置的线程池
    private final Executor asyncExecutor;

    @Value("${photoscore.upload.path:./uploads}")
    private String uploadPath;
    @Autowired
    private SceneClassifier sceneClassifier;
    @jakarta.annotation.PostConstruct
    public void init() {
        scorers = Arrays.asList(
                clarityScorer, noiseScorer, exposureScorer, colorAccuracyScorer,
                toneScorer, resolutionScorer, compositionScorer, lightingScorer,
                themeScorer, momentScorer, impactScorer, styleScorer,
                difficultyScorer, socialValueScorer
        );
        log.info("已注册 {} 个专业评分器", scorers.size());
    }

    @Value("${photoscore.pass-line:60}")
    private BigDecimal passLine;

    private CurrentScoringUser requireCurrentUser() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new IllegalStateException("请先登录后再进行照片评分");
        }

        String username = UserContext.getUsername();
        if (username == null || username.isBlank()) {
            username = "user-" + userId;
        }

        return new CurrentScoringUser(userId, username);
    }

    private <T> T runAsUser(CurrentScoringUser user, CheckedSupplier<T> supplier) throws Exception {
        UserContext.set(user.id(), user.username());
        try {
            return supplier.get();
        } finally {
            UserContext.clear();
        }
    }

    @Override
    public PhotoScoreResponse scoreSinglePhoto(MultipartFile file, String clientIp, String userAgent) {
        log.info("单张照片评分请求: fileName={}", file.getOriginalFilename());
        try {
            CompositeScoreResult result = performFullScoring(file, clientIp, userAgent);
            return buildResponse(result, file);
        } catch (Exception e) {
            log.error("评分失败", e);
            throw new RuntimeException("评分失败: " + e.getMessage());
        }
    }

    private PhotoScoreResponse scoreSinglePhotoWithHash(MultipartFile file,
                                                        String clientIp,
                                                        String userAgent,
                                                        String fileHash) {
        log.info("单张照片评分请求: fileName={}", file.getOriginalFilename());
        try {
            CompositeScoreResult result = performFullScoring(file, clientIp, userAgent, fileHash);
            return buildResponse(result, file);
        } catch (Exception e) {
            log.error("评分失败", e);
            throw new RuntimeException("评分失败: " + e.getMessage());
        }
    }

    private Map<String, PhotoScoreRecord> findExistingRecordsByHash(Collection<String> fileHashes) {
        if (fileHashes == null || fileHashes.isEmpty()) {
            return Collections.emptyMap();
        }

        CurrentScoringUser currentUser = requireCurrentUser();
        List<PhotoScoreRecord> existing = recordMapper.selectByUserIdAndFileHashes(currentUser.id(), fileHashes);
        if (existing == null || existing.isEmpty()) {
            return Collections.emptyMap();
        }

        return existing.stream()
                .filter(record -> record.getFileHash() != null)
                .collect(Collectors.toMap(
                        PhotoScoreRecord::getFileHash,
                        record -> record,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    @Override
    public BatchScoreResponse scoreBatchPhotos(List<MultipartFile> files,
                                               String clientIp, String userAgent) {
        long startTime = System.currentTimeMillis();
        CurrentScoringUser currentUser = requireCurrentUser();
        log.info("批量照片评分请求(并行模式): fileCount={}", files.size());

        // ===== 第1步：计算哈希，内存去重（串行，因为IO密集且很快）=====
        Map<String, MultipartFile> hashToFileMap = new LinkedHashMap<>();
        List<String> duplicateMessages = new ArrayList<>();

        for (MultipartFile file : files) {
            try {
                String fileHash = ImageHashUtil.calculateHash(file);
                if (hashToFileMap.containsKey(fileHash)) {
                    MultipartFile existing = hashToFileMap.get(fileHash);
                    duplicateMessages.add(String.format("图片 '%s' 与 '%s' 内容相同，已跳过",
                            file.getOriginalFilename(), existing.getOriginalFilename()));
                } else {
                    hashToFileMap.put(fileHash, file);
                }
            } catch (Exception e) {
                log.error("计算文件哈希失败: fileName={}", file.getOriginalFilename(), e);
            }
        }

        // ===== 第2步：数据库去重（串行）=====
        Map<String, PhotoScoreRecord> existingRecords = findExistingRecordsByHash(hashToFileMap.keySet());
        Iterator<Map.Entry<String, MultipartFile>> iterator = hashToFileMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, MultipartFile> entry = iterator.next();
            String hash = entry.getKey();
            MultipartFile file = entry.getValue();
            PhotoScoreRecord existingRecord = existingRecords.get(hash);
            if (existingRecord != null) {
                duplicateMessages.add(String.format("图片 '%s' 之前已上传过 (评分: %.2f)，已跳过",
                        file.getOriginalFilename(), existingRecord.getTotalScore()));
                iterator.remove();
            }
        }

        // ===== 第3步：并行评分（核心优化）=====
        List<CompletableFuture<PhotoScoreResponse>> futures = new ArrayList<>();
        for (Map.Entry<String, MultipartFile> entry : hashToFileMap.entrySet()) {
            String fileHash = entry.getKey();
            MultipartFile file = entry.getValue();
            CompletableFuture<PhotoScoreResponse> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return runAsUser(currentUser, () -> scoreSinglePhotoWithHash(file, clientIp, userAgent, fileHash));
                } catch (Exception e) {
                    log.error("并行评分失败: fileName={}", file.getOriginalFilename(), e);
                    // 返回一个失败的默认响应，避免阻塞其他任务
                    return PhotoScoreResponse.builder()
                            .fileName(file.getOriginalFilename())
                            .qualityScore(BigDecimal.ZERO)
                            .isPass(false)
                            .scoreReasons(java.util.Collections.singletonList("评分失败: " + e.getMessage()))
                            .isDuplicate(false)
                            .build();
                }
            }, asyncExecutor); // 使用我们配置的线程池
            futures.add(future);
        }

        // ===== 第4步：等待所有并行任务完成 =====
        List<PhotoScoreResponse> responses = futures.stream()
                .map(future -> {
                    try {
                        return future.get(120, TimeUnit.SECONDS); // 单张照片最多等待2分钟
                    } catch (Exception e) {
                        log.error("等待评分结果超时或异常", e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // ===== 第5步：按分数排序 =====
        responses.sort((r1, r2) -> r2.getQualityScore().compareTo(r1.getQualityScore()));

        // ===== 第6步：构建响应 =====
        long processTime = System.currentTimeMillis() - startTime;
        log.info("批量照片评分完成(并行): totalCount={}, validCount={}, duplicateCount={}, processTime={}ms",
                files.size(), responses.size(), duplicateMessages.size(), processTime);

        return BatchScoreResponse.builder()
                .totalCount(files.size())
                .validCount(responses.size())
                .duplicateCount(duplicateMessages.size())
                .duplicateMessages(duplicateMessages)
                .scores(responses)
                .processTimeMs(processTime)
                .build();
    }

    @Override
    public BatchScoreResponse scoreBatchPhotosWithProgress(List<CachedMultipartFile> files,
                                                           String clientIp,
                                                           String userAgent,
                                                           String taskId,
                                                           ProgressManager progressManager,
                                                           String mode,
                                                           Integer topN) {
        long startTime = System.currentTimeMillis();
        CurrentScoringUser currentUser = requireCurrentUser();
        log.info("批量照片评分请求(带进度): fileCount={}, taskId={}", files.size(), taskId);

        Map<String, CachedMultipartFile> hashToFileMap = new LinkedHashMap<>();
        List<String> duplicateMessages = new ArrayList<>();
        List<PhotoScoreResponse> responses = new ArrayList<>();

        if (progressManager != null && taskId != null) {
            progressManager.initTask(taskId, files.size(), currentUser.id());
        }

        int processed = 0;

        // ===== 第1步：计算哈希，做本次上传内存去重 =====
        for (CachedMultipartFile file : files) {
            try {
                if (file == null || file.isEmpty()) {
                    processed++;
                    if (progressManager != null && taskId != null) {
                        progressManager.updateProgress(taskId, processed);
                    }
                    continue;
                }

                // 建议使用与你入库时一致的 hash 算法
                String fileHash = ImageHashUtil.calculateHash(file);

                if (hashToFileMap.containsKey(fileHash)) {
                    CachedMultipartFile existing = hashToFileMap.get(fileHash);
                    duplicateMessages.add(String.format(
                            "图片 '%s' 与本次上传的 '%s' 内容相同，已跳过重复文件。",
                            file.getOriginalFilename(),
                            existing.getOriginalFilename()
                    ));

                    processed++;
                    if (progressManager != null && taskId != null) {
                        progressManager.updateProgress(taskId, processed);
                    }
                } else {
                    hashToFileMap.put(fileHash, file);
                }
            } catch (Exception e) {
                log.error("计算文件哈希失败: fileName={}", file == null ? "null" : file.getOriginalFilename(), e);

                processed++;
                if (progressManager != null && taskId != null) {
                    progressManager.updateProgress(taskId, processed);
                }
            }
        }

        // ===== 第2步：数据库去重。重点：不再跳过，而是返回历史评分结果 =====
        Iterator<Map.Entry<String, CachedMultipartFile>> iterator = hashToFileMap.entrySet().iterator();

        Map<String, PhotoScoreRecord> progressExistingRecords = findExistingRecordsByHash(hashToFileMap.keySet());

        while (iterator.hasNext()) {
            Map.Entry<String, CachedMultipartFile> entry = iterator.next();
            String hash = entry.getKey();
            CachedMultipartFile file = entry.getValue();

            PhotoScoreRecord existingRecord = progressExistingRecords.get(hash);

            if (existingRecord != null) {
                String msg = String.format(
                        "你的照片已经进行过评分：'%s' 之前已上传过，历史评分为 %.1f 分，本次直接返回之前的评分结果。",
                        file.getOriginalFilename(),
                        existingRecord.getTotalScore()
                );

                duplicateMessages.add(msg);
                responses.add(buildResponseFromRecord(existingRecord, file.getOriginalFilename(), msg));

                iterator.remove();

                processed++;
                if (progressManager != null && taskId != null) {
                    progressManager.updateProgress(taskId, processed);
                }
            }
        }

        // ===== 第3步：剩下的新照片正常评分 =====
        List<Map.Entry<String, CachedMultipartFile>> validFiles = new ArrayList<>(hashToFileMap.entrySet());

        for (Map.Entry<String, CachedMultipartFile> entry : validFiles) {
            CachedMultipartFile file = entry.getValue();
            try {
                responses.add(scoreSinglePhotoWithHash(file, clientIp, userAgent, entry.getKey()));
            } catch (Exception e) {
                log.error("评分失败: {}", file.getOriginalFilename(), e);
            }

            processed++;
            if (progressManager != null && taskId != null) {
                progressManager.updateProgress(taskId, processed);
            }
        }

        // ===== 第4步：按分数排序 =====
        responses.sort((r1, r2) -> r2.getQualityScore().compareTo(r1.getQualityScore()));

        SelectionGroupingResult selectionGrouping = null;
        DeepSeekSelectionReviewResult selectionReview = null;

        // 只有智能选片模式才生成选片分组和未入选原因
        if ("select".equalsIgnoreCase(mode)) {
            selectionGrouping = buildSelectionGroupingResult(responses, topN);
            applyNotSelectedReasons(responses, selectionGrouping);
            selectionReview = generateDeepSeekSelectionReview(responses, topN);
        }

        long processTime = System.currentTimeMillis() - startTime;

        log.info("批量照片评分完成: totalCount={}, responseCount={}, duplicateCount={}, processTime={}ms",
                files.size(),
                responses.size(),
                duplicateMessages.size(),
                processTime
        );

        return BatchScoreResponse.builder()
                .totalCount(files.size())
                .validCount(responses.size())
                .duplicateCount(duplicateMessages.size())
                .duplicateMessages(duplicateMessages)
                .scores(responses)
                .processTimeMs(processTime)
                //只有智能选片模式下才会有这些内容
                .deepSeekSelectionSummary(mergeText(
                        selectionGrouping != null ? selectionGrouping.summary : null,
                        selectionReview != null ? selectionReview.getSelectionSummary() : null))

                .deepSeekSelectionStrategy(mergeText(
                        selectionGrouping != null ? selectionGrouping.strategy : null,
                        selectionReview != null ? selectionReview.getSelectionStrategy() : null))

                .deepSeekKeepAdvice(mergeText(
                        selectionGrouping != null ? selectionGrouping.keepAdvice : null,
                        selectionReview != null ? selectionReview.getKeepAdvice() : null))

                .deepSeekEliminateAdvice(mergeText(
                        selectionGrouping != null ? selectionGrouping.eliminateAdvice : null,
                        selectionReview != null ? selectionReview.getEliminateAdvice() : null))

                .deepSeekRetouchAdvice(mergeText(
                        selectionGrouping != null ? selectionGrouping.retouchAdvice : null,
                        selectionReview != null ? selectionReview.getRetouchAdvice() : null))
                .build();
    }

    private String mergeText(String first, String second) {
        boolean hasFirst = first != null && !first.isBlank();
        boolean hasSecond = second != null && !second.isBlank();

        if (hasFirst && hasSecond) {
            return first + "\n" + second;
        }

        if (hasFirst) {
            return first;
        }

        return hasSecond ? second : null;
    }

    private void applyNotSelectedReasons(List<PhotoScoreResponse> responses,
                                         SelectionGroupingResult groupingResult) {
        if (responses == null || groupingResult == null || groupingResult.notSelectedReasons == null) {
            return;
        }

        for (PhotoScoreResponse response : responses) {
            String fileName = response.getFileName();
            String reason = groupingResult.notSelectedReasons.get(fileName);

            if (reason == null || reason.isBlank()) {
                continue;
            }

            List<String> suggestions = response.getImprovementSuggestions();
            if (suggestions == null) {
                suggestions = new ArrayList<>();
            } else {
                suggestions = new ArrayList<>(suggestions);
            }

            suggestions.removeIf(s -> s != null && s.startsWith("【未入选原因】"));
            suggestions.add("【未入选原因】" + reason);

            response.setImprovementSuggestions(suggestions);
        }
    }



    private String buildNotSelectedReason(PhotoScoreResponse response,
                                          int rank,
                                          int topN) {
        double score = getPhotoScore(response);
        String lowDims = pickLowDimensionsFromResponse(response, 3);

        if (score >= 70) {
            return String.format(
                    "该照片综合分为 %.1f 分，具备保留价值，但本次排名第 %d，未进入 Top %d。可作为备选或同组照片中的第二选择，主要需要继续关注：%s。",
                    score,
                    rank,
                    topN,
                    lowDims
            );
        }

        if (score >= 60) {
            return String.format(
                    "该照片综合分为 %.1f 分，未进入 Top %d，主要原因是整体完成度低于精选照片。建议作为待修素材保留，优先改善：%s。",
                    score,
                    topN,
                    lowDims
            );
        }

        if (score >= 50) {
            return String.format(
                    "该照片综合分为 %.1f 分，处于备选区间，未入选主要因为技术或表达短板较明显。若无特殊记录意义，建议谨慎保留；主要问题集中在：%s。",
                    score,
                    lowDims
            );
        }

        return String.format(
                "该照片综合分为 %.1f 分，整体完成度偏弱，未入选精选列表。除非具有特殊纪念或档案意义，否则建议淘汰；主要短板集中在：%s。",
                score,
                lowDims
        );
    }

    private String pickLowDimensionsFromResponse(PhotoScoreResponse response, int limit) {
        Map<String, BigDecimal> scoreMap = buildScoreDetailMapFromResponse(response);

        if (scoreMap.isEmpty()) {
            return "暂无明确短板维度";
        }

        List<Map.Entry<String, BigDecimal>> entries = new ArrayList<>(scoreMap.entrySet());

        entries.sort((e1, e2) -> {
            BigDecimal v1 = e1.getValue() == null ? BigDecimal.ZERO : e1.getValue();
            BigDecimal v2 = e2.getValue() == null ? BigDecimal.ZERO : e2.getValue();
            return v1.compareTo(v2);
        });

        List<String> parts = new ArrayList<>();

        for (Map.Entry<String, BigDecimal> entry : entries) {
            if (parts.size() >= limit) {
                break;
            }

            BigDecimal value = entry.getValue() == null ? BigDecimal.ZERO : entry.getValue();

            parts.add(String.format(
                    "%s %.1f分",
                    cleanDimensionName(entry.getKey()),
                    value.setScale(1, RoundingMode.HALF_UP).doubleValue()
            ));
        }

        return String.join("、", parts);
    }

    private SelectionGroupingResult buildSelectionGroupingResult(List<PhotoScoreResponse> responses,
                                                                 Integer topN) {
        SelectionGroupingResult result = new SelectionGroupingResult();

        if (responses == null || responses.isEmpty()) {
            result.summary = "本次没有可用于智能选片的照片。";
            result.strategy = "未生成选片策略。";
            result.keepAdvice = "暂无推荐保留照片。";
            result.eliminateAdvice = "暂无备选或淘汰照片。";
            result.retouchAdvice = "暂无修图建议。";
            return result;
        }

        int safeTopN = topN == null ? Math.min(5, responses.size()) : Math.max(1, Math.min(topN, responses.size()));

        List<PhotoScoreResponse> keep = new ArrayList<>();
        List<PhotoScoreResponse> retouch = new ArrayList<>();
        List<PhotoScoreResponse> backup = new ArrayList<>();
        List<PhotoScoreResponse> drop = new ArrayList<>();

        for (int i = 0; i < responses.size(); i++) {
            PhotoScoreResponse r = responses.get(i);
            double score = getPhotoScore(r);

            if (score >= 70) {
                keep.add(r);
            } else if (score >= 60) {
                retouch.add(r);
            } else if (score >= 50) {
                backup.add(r);
            } else {
                drop.add(r);
            }

            if (i >= safeTopN) {
                String reason = buildNotSelectedReason(r, i + 1, safeTopN);
                result.notSelectedReasons.put(r.getFileName(), reason);
            }
        }

        result.summary = String.format(
                "本次智能选片共分析 %d 张照片，按综合评分、清晰度、构图、主题表达和修图潜力分为：推荐保留 %d 张、建议修图 %d 张、备选 %d 张、建议淘汰 %d 张。Top %d 照片优先作为本次精选候选。",
                responses.size(),
                keep.size(),
                retouch.size(),
                backup.size(),
                drop.size(),
                safeTopN
        );

        result.strategy = "本次选片策略：优先保留综合分高、主体表达明确、构图稳定、清晰度和曝光基础较好的照片；对分数略低但仍有氛围感、记录价值或后期修复空间的照片归入建议修图或备选；对清晰度、分辨率、主题表达和画面完成度明显不足的照片归入淘汰组。";

        result.keepAdvice = buildPhotoListText("推荐保留", keep, 8);
        result.retouchAdvice = buildPhotoListText("建议修图", retouch, 8);
        result.eliminateAdvice =
                buildPhotoListText("备选", backup, 8)
                        + "；"
                        + buildPhotoListText("建议淘汰", drop, 8);

        return result;
    }

    private double getPhotoScore(PhotoScoreResponse response) {
        if (response == null || response.getQualityScore() == null) {
            return 0.0;
        }

        return response.getQualityScore().doubleValue();
    }

    private static class SelectionGroupingResult {
        String summary;
        String strategy;
        String keepAdvice;
        String eliminateAdvice;
        String retouchAdvice;
        Map<String, String> notSelectedReasons = new LinkedHashMap<>();
    }

    private String buildPhotoListText(String title,
                                      List<PhotoScoreResponse> photos,
                                      int limit) {
        if (photos == null || photos.isEmpty()) {
            return title + "：暂无";
        }

        List<String> parts = new ArrayList<>();

        for (int i = 0; i < photos.size() && i < limit; i++) {
            PhotoScoreResponse r = photos.get(i);
            parts.add(String.format(
                    "%s（%.1f分）",
                    r.getFileName(),
                    getPhotoScore(r)
            ));
        }

        if (photos.size() > limit) {
            parts.add("等 " + photos.size() + " 张");
        }

        return title + "：" + String.join("、", parts);
    }

    private DeepSeekSelectionReviewResult generateDeepSeekSelectionReview(List<PhotoScoreResponse> responses, Integer topN) {
        try {
            if (deepSeekAiService == null || !deepSeekAiService.isEnabled()) {
                return null;
            }

            if (responses == null || responses.isEmpty()) {
                return null;
            }

            int safeTopN = topN == null ? Math.min(5, responses.size()) : Math.max(1, Math.min(topN, responses.size()));

            List<DeepSeekSelectionReviewRequest.PhotoItem> items = responses.stream()
                    .map(r -> DeepSeekSelectionReviewRequest.PhotoItem.builder()
                            .id(r.getId())
                            .fileName(r.getFileName())
                            .score(r.getQualityScore())
                            .pass(Boolean.TRUE.equals(r.getIsPass()))
                            .comments(r.getScoreReasons())
                            .suggestions(r.getImprovementSuggestions())
                            .build())
                    .toList();

            DeepSeekSelectionReviewResult result = deepSeekAiService.generateSelectionReview(
                    DeepSeekSelectionReviewRequest.builder()
                            .topN(safeTopN)
                            .photos(items)
                            .build()
            );

            if (result != null && result.isSuccess()) {
                return result;
            }

            return null;
        } catch (Exception e) {
            log.warn("DeepSeek 智能选片报告生成异常，已跳过: {}", e.getMessage(), e);
            return null;
        }
    }

    private PhotoScoreResponse buildResponseFromRecord(PhotoScoreRecord record,
                                                       String currentUploadFileName,
                                                       String duplicateMessage) {
        Map<String, BigDecimal> details = new LinkedHashMap<>();

        putScore(details, "清晰度评分", record.getClarityScore());
        putScore(details, "噪点控制评分", record.getNoiseScore());
        putScore(details, "曝光控制评分", record.getExposureScore());
        putScore(details, "色彩准确度评分", record.getColorAccuracyScore());
        putScore(details, "影调表现评分", record.getToneScore());
        putScore(details, "分辨率评分", record.getResolutionScore());
        putScore(details, "构图水平评分", record.getCompositionScore());
        putScore(details, "用光与色彩评分", record.getLightingScore());
        putScore(details, "主题表达评分", record.getThemeScore());
        putScore(details, "瞬间捕捉评分", record.getMomentScore());
        putScore(details, "冲击力评分", record.getImpactScore());
        putScore(details, "风格评分", record.getStyleScore());
        putScore(details, "拍摄难度评分", record.getDifficultyScore());
        putScore(details, "社会价值评分", record.getSocialValueScore());

        List<String> reasons = parseJsonList(record.getScoreReason());
        List<String> suggestions = parseJsonList(record.getImprovementSuggestions());

        if (duplicateMessage != null && !duplicateMessage.isBlank()) {
            reasons.add(0, "【历史记录】" + duplicateMessage);
        }

        return PhotoScoreResponse.builder()
                .id(record.getId())
                // 页面上显示用户这次上传的文件名；缩略图仍然使用历史记录里的 imagePath
                .fileName(currentUploadFileName == null || currentUploadFileName.isBlank()
                        ? record.getFileName()
                        : currentUploadFileName)
                .fileSize(record.getFileSize())
                .dimension(record.getWidth() + "x" + record.getHeight())
                .qualityScore(record.getTotalScore())
                .isPass(record.getIsPass() != null && record.getIsPass() == 1)
                .scoreDetails(details)
                .scoreReasons(reasons)
                .improvementSuggestions(suggestions)
                .isDuplicate(true)
                .duplicateMessage(duplicateMessage)
                .imagePath(record.getImagePath())
                .build();
    }

    private void putScore(Map<String, BigDecimal> details, String name, BigDecimal value) {
        if (value != null) {
            details.put(name, value);
        }
    }

    private List<String> parseJsonList(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }

        try {
            return objectMapper.readValue(
                    json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
            );
        } catch (Exception e) {
            log.warn("解析 JSON 数组失败: {}", json, e);
            List<String> fallback = new ArrayList<>();
            fallback.add(json);
            return fallback;
        }
    }






    @Override
    public CompareResult comparePhotos(MultipartFile file1,
                                       MultipartFile file2,
                                       String clientIp,
                                       String userAgent) throws IOException {
        MultipartFile safe1 = cacheMultipartFile(file1);
        MultipartFile safe2 = cacheMultipartFile(file2);

        PhotoScoreResponse resp1 = scoreOrReturnHistory(safe1, clientIp, userAgent);
        PhotoScoreResponse resp2 = scoreOrReturnHistory(safe2, clientIp, userAgent);

        BigDecimal diff = resp1.getQualityScore().subtract(resp2.getQualityScore());

        Map<String, BigDecimal> dimDiff = new HashMap<>();
        if (resp1.getScoreDetails() != null && resp2.getScoreDetails() != null) {
            resp1.getScoreDetails().forEach((k, v) -> {
                BigDecimal v2 = resp2.getScoreDetails().getOrDefault(k, BigDecimal.ZERO);
                dimDiff.put(k, v.subtract(v2));
            });
        }

        String advantage;
        if (diff.compareTo(BigDecimal.ZERO) > 0) {
            advantage = String.format("照片1整体优于照片2，总分高出 %.1f 分", diff);
        } else if (diff.compareTo(BigDecimal.ZERO) < 0) {
            advantage = String.format("照片2整体优于照片1，总分高出 %.1f 分", diff.abs());
        } else {
            advantage = "两张照片总分相同，各有千秋";
        }

        List<String> historyNotes = new ArrayList<>();
        if (resp1.getDuplicateMessage() != null && !resp1.getDuplicateMessage().isBlank()) {
            historyNotes.add("照片1：" + resp1.getDuplicateMessage());
        }
        if (resp2.getDuplicateMessage() != null && !resp2.getDuplicateMessage().isBlank()) {
            historyNotes.add("照片2：" + resp2.getDuplicateMessage());
        }

        if (!historyNotes.isEmpty()) {
            advantage = String.join("；", historyNotes) + "。 " + advantage;
        }

        CompareResult compareResult = CompareResult.builder()
                .photo1(resp1)
                .photo2(resp2)
                .scoreDiff(diff)
                .dimensionDiff(dimDiff)
                .advantage(advantage)
                .build();
        // 新增：对比最终推荐 + 维度差异 Top 3
        applyUserFriendlyCompareConclusion(compareResult, resp1, resp2);
        // 本地对比结果构建完成后，再调用 DeepSeek 生成专业对比分析
        applyDeepSeekCompareReview(compareResult, resp1, resp2);

        return compareResult;
    }

    private void applyUserFriendlyCompareConclusion(CompareResult compareResult,
                                                    PhotoScoreResponse resp1,
                                                    PhotoScoreResponse resp2) {
        if (compareResult == null || resp1 == null || resp2 == null) {
            return;
        }

        double score1 = getPhotoScore(resp1);
        double score2 = getPhotoScore(resp2);
        double diff = Math.abs(score1 - score2);

        Map<String, BigDecimal> map1 = buildScoreDetailMapFromResponse(resp1);
        Map<String, BigDecimal> map2 = buildScoreDetailMapFromResponse(resp2);

        String dimensionTop3 = buildCompareDimensionDiffTop3(map1, map2);
        String photo1Adv = buildPhotoAdvantageTop3("照片1", map1, map2);
        String photo2Adv = buildPhotoAdvantageTop3("照片2", map2, map1);

        String recommendation;

        if (diff < 3) {
            recommendation = String.format(
                    "两张照片综合分非常接近，照片1为 %.1f 分，照片2为 %.1f 分，不建议只按分数决定。若用于主图，应优先选择主体更明确、构图更稳定的一张；若用于氛围或记录，可以根据具体用途分别保留。",
                    score1,
                    score2
            );
        } else if (score1 > score2) {
            recommendation = String.format(
                    "推荐选择照片1。照片1综合分 %.1f 分，高于照片2的 %.1f 分，整体完成度、可用性或后期潜力更强，更适合作为本组主图候选。",
                    score1,
                    score2
            );
        } else {
            recommendation = String.format(
                    "推荐选择照片2。照片2综合分 %.1f 分，高于照片1的 %.1f 分，整体完成度、可用性或后期潜力更强，更适合作为本组主图候选。",
                    score2,
                    score1
            );
        }

        compareResult.setFinalRecommendation(recommendation);
        compareResult.setDimensionDiffTop3(dimensionTop3);
        compareResult.setPhoto1AdvantageTop3(photo1Adv);
        compareResult.setPhoto2AdvantageTop3(photo2Adv);

        String oldAdvantage = compareResult.getAdvantage();

        String newAdvantage =
                "【最终推荐】" + recommendation
                        + "【维度差异 Top 3】" + dimensionTop3
                        + "【照片1优势】" + photo1Adv
                        + "【照片2优势】" + photo2Adv
                        + (oldAdvantage == null || oldAdvantage.isBlank() ? "" : "【原始对比结论】" + oldAdvantage);

        compareResult.setAdvantage(newAdvantage);
    }

    private Map<String, BigDecimal> buildScoreDetailMapFromResponse(PhotoScoreResponse r) {
        Map<String, BigDecimal> scoreDetails = new LinkedHashMap<>();

        if (r == null || r.getScoreDetails() == null || r.getScoreDetails().isEmpty()) {
            return scoreDetails;
        }

        r.getScoreDetails().forEach((key, value) -> {
            if (key != null && value != null) {
                scoreDetails.put(key, value);
            }
        });

        return scoreDetails;
    }

    private String buildPhotoAdvantageTop3(String label,
                                           Map<String, BigDecimal> current,
                                           Map<String, BigDecimal> other) {
        List<DimensionDiffItem> diffs = buildDimensionDiffItems(current, other);

        List<DimensionDiffItem> positive = new ArrayList<>();

        for (DimensionDiffItem item : diffs) {
            if (item.diff > 0.1) {
                positive.add(item);
            }
        }

        positive.sort((a, b) -> Double.compare(b.diff, a.diff));

        if (positive.isEmpty()) {
            return label + "没有明显高于对方的维度，建议结合画面内容和用途判断。";
        }

        List<String> parts = new ArrayList<>();

        for (int i = 0; i < positive.size() && i < 3; i++) {
            DimensionDiffItem item = positive.get(i);
            parts.add(String.format(
                    "%s +%.1f分",
                    cleanDimensionName(item.name),
                    item.diff
            ));
        }

        return label + "主要优势为：" + String.join("、", parts) + "。";
    }

    private List<DimensionDiffItem> buildDimensionDiffItems(Map<String, BigDecimal> map1,
                                                            Map<String, BigDecimal> map2) {
        List<DimensionDiffItem> result = new ArrayList<>();

        if (map1 == null || map2 == null) {
            return result;
        }

        for (Map.Entry<String, BigDecimal> entry : map1.entrySet()) {
            String name = entry.getKey();

            if (!map2.containsKey(name)) {
                continue;
            }

            BigDecimal v1 = entry.getValue() == null ? BigDecimal.ZERO : entry.getValue();
            BigDecimal v2 = map2.get(name) == null ? BigDecimal.ZERO : map2.get(name);

            DimensionDiffItem item = new DimensionDiffItem();
            item.name = name;
            item.diff = v1.subtract(v2).doubleValue();

            result.add(item);
        }

        return result;
    }


    private static class DimensionDiffItem {
        String name;
        double diff;
    }

    private String buildCompareDimensionDiffTop3(Map<String, BigDecimal> map1,
                                                 Map<String, BigDecimal> map2) {
        List<DimensionDiffItem> diffs = buildDimensionDiffItems(map1, map2);

        if (diffs.isEmpty()) {
            return "暂无可比较的维度差异。";
        }

        diffs.sort((a, b) -> Double.compare(Math.abs(b.diff), Math.abs(a.diff)));

        List<String> parts = new ArrayList<>();

        for (int i = 0; i < diffs.size() && i < 3; i++) {
            DimensionDiffItem item = diffs.get(i);

            if (Math.abs(item.diff) < 0.1) {
                continue;
            }

            String winner = item.diff > 0 ? "照片1更高" : "照片2更高";

            parts.add(String.format(
                    "%s：%s %.1f分",
                    cleanDimensionName(item.name),
                    winner,
                    Math.abs(item.diff)
            ));
        }

        if (parts.isEmpty()) {
            return "两张照片各维度差异较小，建议结合用途、主体表达和个人偏好判断。";
        }

        return String.join("；", parts) + "。";
    }

    private void applyDeepSeekCompareReview(CompareResult compareResult,
                                            PhotoScoreResponse resp1,
                                            PhotoScoreResponse resp2) {
        try {
            if (deepSeekAiService == null || !deepSeekAiService.isEnabled()) {
                return;
            }

            DeepSeekCompareReviewResult result = deepSeekAiService.generateCompareReview(
                    DeepSeekCompareReviewRequest.builder()
                            .photo1(toDeepSeekCompareItem("照片1", resp1))
                            .photo2(toDeepSeekCompareItem("照片2", resp2))
                            .localConclusion(compareResult.getAdvantage())
                            .build()
            );

            if (result == null || !result.isSuccess()) {
                log.debug("DeepSeek 照片对比分析未生成: {}",
                        result == null ? "null" : result.getErrorMessage());
                return;
            }

            compareResult.setDeepSeekCompareConclusion(result.getCompareConclusion());
            compareResult.setDeepSeekCompareReason(result.getCompareReason());
            compareResult.setDeepSeekUsageAdvice(result.getUsageAdvice());
            compareResult.setDeepSeekRetouchAdvice(result.getRetouchAdvice());

        } catch (Exception e) {
            log.warn("DeepSeek 照片对比分析异常，已跳过，不影响照片对比功能: {}", e.getMessage(), e);
        }
    }

    private DeepSeekCompareReviewRequest.PhotoItem toDeepSeekCompareItem(String label, PhotoScoreResponse r) {
        return DeepSeekCompareReviewRequest.PhotoItem.builder()
                .label(label)
                .fileName(r.getFileName())
                .score(r.getQualityScore())
                .scoreDetails(r.getScoreDetails())
                .comments(r.getScoreReasons())
                .suggestions(r.getImprovementSuggestions())
                .build();
    }

    private PhotoScoreResponse scoreOrReturnHistory(MultipartFile file,
                                                    String clientIp,
                                                    String userAgent) throws IOException {
        String fileHash = ImageHashUtil.calculateHash(file);
        CurrentScoringUser currentUser = requireCurrentUser();
        PhotoScoreRecord existingRecord = recordMapper.selectByUserIdAndFileHash(currentUser.id(), fileHash);

        if (existingRecord != null) {
            String msg = String.format(
                    "你的照片已经进行过评分：'%s' 之前已上传过，历史评分为 %.1f 分，本次直接返回之前的评分结果。",
                    file.getOriginalFilename(),
                    existingRecord.getTotalScore()
            );

            return buildResponseFromRecord(existingRecord, file.getOriginalFilename(), msg);
        }

        return scoreSinglePhotoWithHash(file, clientIp, userAgent, fileHash);
    }

    // 辅助方法：将上传文件缓存到内存
    private MultipartFile cacheMultipartFile(MultipartFile file) throws IOException {
        byte[] content = file.getBytes();
        return new MultipartFile() {
            @Override public String getName() { return file.getName(); }
            @Override public String getOriginalFilename() { return file.getOriginalFilename(); }
            @Override public String getContentType() { return file.getContentType(); }
            @Override public boolean isEmpty() { return content.length == 0; }
            @Override public long getSize() { return content.length; }
            @Override public byte[] getBytes() { return content; }
            @Override public InputStream getInputStream() { return new ByteArrayInputStream(content); }
            @Override public void transferTo(File dest) throws IOException { Files.write(dest.toPath(), content); }
        };
    }

    /**
     * 将指定记录中的照片文件打包成ZIP流
     */
    @Override
    public void downloadPhotosAsZip(List<PhotoScoreRecord> records, OutputStream outputStream) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(outputStream)) {
            // 用于防止文件名重复
            Set<String> usedNames = new HashSet<>();

            for (PhotoScoreRecord record : records) {
                String imagePath = record.getImagePath();
                if (imagePath == null) continue;

                // 构建绝对路径（去掉开头的 /uploads/ 前缀）
                String relativePath = imagePath.replaceFirst("^/uploads/", "");
                Path absolutePath = Paths.get(uploadPath, relativePath).toAbsolutePath();
                File file = absolutePath.toFile();

                if (!file.exists()) {
                    log.warn("打包下载时文件不存在: {}", absolutePath);
                    continue;
                }

                // 生成唯一的文件名
                String baseName = record.getFileName();
                String entryName = baseName;
                int counter = 1;
                while (usedNames.contains(entryName)) {
                    int dotIndex = baseName.lastIndexOf('.');
                    if (dotIndex > 0) {
                        entryName = baseName.substring(0, dotIndex) + "_" + counter + baseName.substring(dotIndex);
                    } else {
                        entryName = baseName + "_" + counter;
                    }
                    counter++;
                }
                usedNames.add(entryName);

                // 写入ZIP条目
                ZipEntry zipEntry = new ZipEntry(entryName);
                zos.putNextEntry(zipEntry);
                Files.copy(file.toPath(), zos);
                zos.closeEntry();
            }
        }
    }

    /**
     * 执行完整评分，包含图像缩放优化
     */
    public CompositeScoreResult performFullScoring(MultipartFile file, String clientIp, String userAgent) throws IOException {
        return performFullScoring(file, clientIp, userAgent, ImageHashUtil.calculateHash(file));
    }

    public CompositeScoreResult performFullScoring(MultipartFile file,
                                                   String clientIp,
                                                   String userAgent,
                                                   String fileHash) throws IOException {
        long startTime = System.currentTimeMillis();
        CurrentScoringUser currentUser = requireCurrentUser();
        String safeFileHash = fileHash == null || fileHash.isBlank()
                ? ImageHashUtil.calculateHash(file)
                : fileHash;

        BufferedImage originalImage = ImageIO.read(file.getInputStream());
        if (originalImage == null) throw new IOException("无法解析图片文件");

        // 缩放图像以加速评分
        BufferedImage processedImage = resizeImage(originalImage, 1200);
        OpenCVUtil.init();

        // --- 保存照片文件到 D:/photo_uploads ---
        String originalFilename = file.getOriginalFilename();
        String fileExtension = getFileExtension(originalFilename);
        // 按日期分目录存储
        String dateDir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String fileName = UUID.randomUUID() + "." + fileExtension;
        // 数据库存储的相对路径
        String relativePath = "/uploads/" + dateDir + "/" + fileName;
        // 绝对路径
        Path absoluteTargetPath = Paths.get(uploadPath, dateDir, fileName).toAbsolutePath();

        try {
            Files.createDirectories(absoluteTargetPath.getParent());
            file.transferTo(absoluteTargetPath.toFile());
            log.debug("照片已保存至: {}", absoluteTargetPath);
        } catch (IOException e) {
            log.error("保存照片文件失败: {}", relativePath, e);
            // 如果保存失败，将相对路径置空，这样前端会显示占位符
            relativePath = null;
        }

        // 执行所有评分器
        List<ScoringResult> results = new ArrayList<>();
        List<String> comments = new ArrayList<>();
        List<String> allSuggestions = new ArrayList<>();

        String sceneCategory;
        try (ScoringImageContext scoringContext = ScoringImageContext.from(processedImage)) {
            for (BaseScorer scorer : scorers) {
                try {
                    ScoringResult result = scorer.score(scoringContext);
                    results.add(result);
                    if (result.getComment() != null) {
                        comments.add("【" + result.getScorerName() + "】" + result.getComment());
                    }
                    if (result.getSuggestions() != null) {
                        allSuggestions.addAll(result.getSuggestions());
                    }
                } catch (Exception e) {
                    log.error("评分器 {} 失败: {}", scorer.getScorerName(), e.getMessage());
                }
            }

            // 场景分类复用同一份 OpenCV Mat，避免再次转换图片。
            sceneCategory = sceneClassifier.classify(scoringContext);
        }

        // ===== 计算本地14项评分器分类得分 =====
// 注意：这三个分数都是 0-1 区间
        double technicalScore = calcCategoryScore(results, "TECHNICAL");
        double aestheticScore = calcCategoryScore(results, "AESTHETIC");
        double comprehensiveScore = calcCategoryScore(results, "COMPREHENSIVE");

// ===== NIMA评分 =====
// 当前你只有 nima_aesthetic.onnx，没有独立的 nima_technical.onnx。
// 所以：
// 1. nimaAesthetic 使用真实 NIMA 模型
// 2. nimaTechnical 使用本地技术类评分器 technicalScore，避免两个 NIMA 分数完全一样
        double nimaAesthetic = 0.65;

        if (nimaScorer != null && nimaScorer.isAvailable()) {
            nimaAesthetic = nimaScorer.scoreAesthetic(processedImage);
        }

// 没有独立 NIMA 技术模型时，技术参考分使用本地技术类综合分
        double nimaTechnical = technicalScore;

// ===== 本地基础综合分 =====
// 仍然以你原来的14项评分器为主体
        double baseScore = technicalScore * 0.30
                + aestheticScore * 0.40
                + comprehensiveScore * 0.30;

// ===== NIMA深度参考分 =====
// NIMA 更偏审美质量，所以美学权重大一点
        double nimaScore = nimaTechnical * 0.40
                + nimaAesthetic * 0.60;

// ===== 最终本地量化分 =====
// 14项评分器占 85%，NIMA占 15%
// 这样 NIMA 会真正影响总分，但不会破坏你原来的评分体系
        double totalRaw = baseScore * 0.85
                + nimaScore * 0.15;

        log.debug("场景分类结果: {}", sceneCategory);

// 生成总体评价
        String overallComment = generateOverallComment(totalRaw, sceneCategory);
        comments.add(0, overallComment);

// ===== 生成最终总体建议（只挑最差的3个） =====
// 注意：必须放在序列化之前，否则不会写入数据库
        String finalSuggestion = generateFinalSuggestion(results);
        allSuggestions.add(0, "【优先改进】" + finalSuggestion);

// ===== 豆包视觉 AI：参考分 + 评分校验 + 专业文案 =====
// 影子模式：只追加评语和建议，不改变 totalRaw / qualityScore
        double finalQualityScore = applyDoubaoVisionReviewAndReturnFinalScore(
                originalFilename,
                sceneCategory,
                totalRaw,
                technicalScore,
                aestheticScore,
                comprehensiveScore,
                results,
                overallComment,
                finalSuggestion,
                processedImage,
                comments,
                allSuggestions
        );
        finalQualityScore = roundOne(clamp(finalQualityScore, 0, 100));

        appendDeepSeekPhotoReport(
                originalFilename,
                sceneCategory,
                scoreToBigDecimal(finalQualityScore),
                scoreToBigDecimal(normalizeScoreTo100(totalRaw)),
                toBigDecimal(technicalScore),
                toBigDecimal(aestheticScore),
                toBigDecimal(comprehensiveScore),
                results,
                comments,
                allSuggestions
        );
        // 统一本地14项评分器、NIMA/OpenCV、豆包视觉、DeepSeek的结果，生成用户最容易理解的最终结论
        insertUnifiedProfessionalConclusion(
                comments,
                allSuggestions,
                finalQualityScore,
                normalizeScoreTo100(totalRaw),
                results
        );
        // 新增：评分可信度、适合用途、主要优势/问题 Top 3
        appendUserFriendlyReviewSummary(
                comments,
                allSuggestions,
                finalQualityScore,
                normalizeScoreTo100(totalRaw),
                results,
                processedImage
        );
        long elapsed = System.currentTimeMillis() - startTime;
        log.info("照片评分完成: fileName={}, 耗时={}ms, 最终分={}, 本地量化分={}",
                originalFilename,
                elapsed,
                scoreToBigDecimal(finalQualityScore),
                scoreToBigDecimal(normalizeScoreTo100(totalRaw))
        );

        CompositeScoreResult composite = CompositeScoreResult.builder()
                .totalScore(scoreToBigDecimal(finalQualityScore))
                .technicalScore(toBigDecimal(technicalScore))
                .aestheticScore(toBigDecimal(aestheticScore))
                .comprehensiveScore(toBigDecimal(comprehensiveScore))
                .nimaAestheticScore(toBigDecimal(nimaAesthetic))
                .nimaTechnicalScore(toBigDecimal(nimaTechnical))
                .scoringResults(results)
                .comments(comments)
                .suggestions(allSuggestions)
                .imageWidth(processedImage.getWidth())
                .imageHeight(processedImage.getHeight())
                .elapsedMs(elapsed)
                .imagePath(relativePath)
                .build();
        // 序列化评语和建议
        String scoreReasonJson;
        String suggestionsJson;
        try {
            scoreReasonJson = objectMapper.writeValueAsString(comments);
            suggestionsJson = objectMapper.writeValueAsString(allSuggestions);
        } catch (Exception e) {
            log.error("序列化评分理由失败", e);
            scoreReasonJson = "[]";
            suggestionsJson = "[]";
        }

        // 保存记录到数据库，包含 imagePath
        PhotoScoreRecord record = PhotoScoreRecord.builder()
                .userId(currentUser.id())
                .username(currentUser.username())
                .fileName(originalFilename)
                .fileHash(safeFileHash)
                .fileSize(file.getSize())
                .width(processedImage.getWidth())
                .height(processedImage.getHeight())
                .format(fileExtension.toUpperCase())
                .totalScore(composite.getTotalScore())
                .isPass(composite.getTotalScore().compareTo(passLine) >= 0 ? 1 : 0)
                .technicalScore(composite.getTechnicalScore())
                .aestheticScore(composite.getAestheticScore())
                .comprehensiveScore(composite.getComprehensiveScore())
                .nimaTechnicalScore(composite.getNimaTechnicalScore())
                .nimaAestheticScore(composite.getNimaAestheticScore())
                .clarityScore(getScoreByName(results, "清晰度评分"))
                .noiseScore(getScoreByName(results, "噪点控制评分"))
                .exposureScore(getScoreByName(results, "曝光控制评分"))
                .colorAccuracyScore(getScoreByName(results, "色彩准确度评分"))
                .toneScore(getScoreByName(results, "影调表现评分"))
                .resolutionScore(getScoreByName(results, "分辨率评分"))
                .compositionScore(getScoreByName(results, "构图水平评分"))
                .lightingScore(getScoreByName(results, "用光与色彩评分"))
                .themeScore(getScoreByName(results, "主题表达评分"))
                .momentScore(getScoreByName(results, "瞬间捕捉评分"))
                .impactScore(getScoreByName(results, "冲击力评分"))
                .styleScore(getScoreByName(results, "风格评分"))
                .difficultyScore(getScoreByName(results, "拍摄难度评分"))
                .socialValueScore(getScoreByName(results, "社会价值评分"))
                .scoreReason(scoreReasonJson)
                .improvementSuggestions(suggestionsJson)
                .clientIp(clientIp)
                .userAgent(userAgent)
                .imagePath(relativePath)           // <--- 关键：保存相对路径
                .processTimeMs((int) elapsed)
                .createdTime(LocalDateTime.now())
                .updatedTime(LocalDateTime.now())
                .build();

        recordMapper.insert(record);
        composite.setId(record.getId());
        composite.setSceneCategory(sceneCategory);
        return composite;
    }

    private void appendUserFriendlyReviewSummary(List<String> comments,
                                                 List<String> allSuggestions,
                                                 double finalQualityScore,
                                                 double localScore100,
                                                 List<ScoringResult> results,
                                                 BufferedImage image) {
        if (comments == null || allSuggestions == null) {
            return;
        }

        double finalScore = roundOne(clamp(finalQualityScore, 0, 100));
        double localScore = roundOne(clamp(localScore100, 0, 100));

        Map<String, BigDecimal> scoreMap = buildScoreDetailMap(results);

        String confidenceText = buildScoreConfidenceText(finalScore, localScore, scoreMap, image);
        String usageText = buildSuitableUsageText(finalScore, scoreMap);
        String strengthText = pickDimensionText(scoreMap, true, 3);
        String weaknessText = pickDimensionText(scoreMap, false, 3);
        String priorityFixText = buildPriorityFixText(finalScore, weaknessText);

        // 防止重新评分时重复插入
        comments.removeIf(c -> c != null && (
                c.startsWith("【评分可信度】")
                        || c.startsWith("【适合用途】")
                        || c.startsWith("【主要优势 Top 3】")
                        || c.startsWith("【主要问题 Top 3】")
        ));

        allSuggestions.removeIf(s -> s != null && (
                s.startsWith("【优先处理建议】")
                        || s.startsWith("【适合用途建议】")
        ));

        List<String> headerComments = new ArrayList<>();
        headerComments.add("【评分可信度】" + confidenceText);
        headerComments.add("【适合用途】" + usageText);
        headerComments.add("【主要优势 Top 3】" + strengthText);
        headerComments.add("【主要问题 Top 3】" + weaknessText);

        comments.addAll(0, headerComments);

        List<String> headerSuggestions = new ArrayList<>();
        headerSuggestions.add("【优先处理建议】" + priorityFixText);
        headerSuggestions.add("【适合用途建议】" + usageText);

        allSuggestions.addAll(0, headerSuggestions);
    }

    private String buildPriorityFixText(double finalScore, String weaknessText) {
        if (finalScore >= 80) {
            return "照片基础较好，后期应以微调为主。重点检查白平衡、局部对比、主体锐化和边缘干扰元素，避免过度修图破坏原始质感。";
        }

        if (finalScore >= 70) {
            return "建议保留并进行针对性修图。优先处理：" + weaknessText + "后期重点应放在裁剪构图、提升层次、适度锐化、校正色彩和减少干扰元素上。";
        }

        if (finalScore >= 60) {
            return "建议作为待修素材保留。优先修正：" + weaknessText + "如果修图后仍无法改善清晰度、分辨率、色彩或主体表达，则不建议作为最终精选。";
        }

        return "建议谨慎保留。当前主要短板为：" + weaknessText + "除非照片具有特殊记录意义，否则不建议投入较多后期成本。";
    }

    private String buildSuitableUsageText(double finalScore,
                                          Map<String, BigDecimal> scoreMap) {
        BigDecimal clarityScore = findScoreByKeyword(scoreMap, "清晰");
        BigDecimal resolutionScore = findScoreByKeyword(scoreMap, "分辨率");
        BigDecimal themeScore = findScoreByKeyword(scoreMap, "主题");
        BigDecimal compositionScore = findScoreByKeyword(scoreMap, "构图");

        boolean lowClarity = clarityScore != null && clarityScore.compareTo(BigDecimal.valueOf(45)) < 0;
        boolean lowResolution = resolutionScore != null && resolutionScore.compareTo(BigDecimal.valueOf(45)) < 0;
        boolean strongTheme = themeScore != null && themeScore.compareTo(BigDecimal.valueOf(75)) >= 0;
        boolean goodComposition = compositionScore != null && compositionScore.compareTo(BigDecimal.valueOf(75)) >= 0;

        if (finalScore >= 85) {
            return "适合作为精选作品、作品集候选、社交媒体主图、活动宣传图或正式展示素材。若原图分辨率足够，也可考虑打印输出。";
        }

        if (finalScore >= 75) {
            if (lowResolution || lowClarity) {
                return "适合社交媒体发布、项目备选图、普通展示和修图后使用；但因清晰度或分辨率存在限制，不建议大尺寸打印或高规格商业输出。";
            }
            return "适合社交媒体发布、活动记录、旅行/纪实分享、普通宣传素材和修图后展示。";
        }

        if (finalScore >= 70) {
            if (strongTheme || goodComposition) {
                return "适合保留并进行后期优化，可作为社交配图、公众号配图、纪实记录、项目备选素材或情绪氛围图使用。";
            }
            return "适合作为普通记录、社交配图和待修素材使用；如需正式展示，建议先完成裁剪、色彩和清晰度优化。";
        }

        if (finalScore >= 60) {
            return "更适合作为记录留存、情绪配图、资料素材或待修照片，不建议直接用于商业输出、正式宣传、打印放大或摄影比赛。";
        }

        return "更适合作为参考记录或特殊纪念留存，不建议作为正式展示、商业素材、精选照片或高质量社交发布图使用。";
    }

    private BigDecimal findScoreByKeyword(Map<String, BigDecimal> scoreMap, String keyword) {
        if (scoreMap == null || keyword == null) {
            return null;
        }

        for (Map.Entry<String, BigDecimal> entry : scoreMap.entrySet()) {
            if (entry.getKey() != null && entry.getKey().contains(keyword)) {
                return entry.getValue();
            }
        }

        return null;
    }

    private String buildScoreConfidenceText(double finalScore,
                                            double localScore,
                                            Map<String, BigDecimal> scoreMap,
                                            BufferedImage image) {
        double diff = Math.abs(finalScore - localScore);

        int confidence = 100;
        List<String> reasons = new ArrayList<>();

        if (diff <= 5) {
            reasons.add("本地量化分与最终融合分差距较小，多方判断较一致");
        } else if (diff <= 12) {
            confidence -= 12;
            reasons.add("本地量化分与最终融合分存在一定差异，豆包视觉复核对结果进行了校准");
        } else {
            confidence -= 28;
            reasons.add("本地量化分与最终融合分差异较大，说明照片存在算法判断分歧");
        }

        int lowDimensionCount = 0;
        for (BigDecimal value : scoreMap.values()) {
            if (value != null && value.compareTo(BigDecimal.valueOf(30)) < 0) {
                lowDimensionCount++;
            }
        }

        if (lowDimensionCount >= 4) {
            confidence -= 18;
            reasons.add("低分维度较多，照片质量稳定性不足");
        } else if (lowDimensionCount >= 2) {
            confidence -= 8;
            reasons.add("存在少量明显短板维度，需要结合实际用途判断");
        }

        BigDecimal resolutionScore = findScoreByKeyword(scoreMap, "分辨率");
        if (resolutionScore != null && resolutionScore.compareTo(BigDecimal.valueOf(30)) < 0) {
            confidence -= 8;
            reasons.add("分辨率偏低，影响放大查看和专业输出判断");
        }

        BigDecimal clarityScore = findScoreByKeyword(scoreMap, "清晰");
        if (clarityScore != null && clarityScore.compareTo(BigDecimal.valueOf(30)) < 0) {
            confidence -= 8;
            reasons.add("清晰度偏低，细节判断可靠性下降");
        }

        if (image != null) {
            double megapixels = image.getWidth() * image.getHeight() / 1_000_000.0;
            if (megapixels < 1.2) {
                confidence -= 8;
                reasons.add("图片像素较低，系统对细节和画质的判断会更谨慎");
            }
        }

        try {
            if (nimaScorer == null || !nimaScorer.isAvailable()) {
                confidence -= 10;
                reasons.add("NIMA 模型不可用或处于降级状态，深度审美参考不足");
            }
        } catch (Exception ignored) {
            confidence -= 10;
            reasons.add("NIMA 状态异常，深度审美参考不足");
        }

        confidence = Math.max(35, Math.min(100, confidence));

        String level;
        if (confidence >= 80) {
            level = "高";
        } else if (confidence >= 60) {
            level = "中";
        } else {
            level = "低";
        }

        return String.format(
                "%s。系统可信度约 %d%%。判断依据：%s。",
                level,
                confidence,
                String.join("；", reasons)
        );
    }

    private void insertUnifiedProfessionalConclusion(List<String> comments,
                                                     List<String> allSuggestions,
                                                     double finalQualityScore,
                                                     double localScore100,
                                                     List<ScoringResult> results) {
        if (comments == null || allSuggestions == null) {
            return;
        }

        double finalScore = roundOne(clamp(finalQualityScore, 0, 100));
        double localScore = roundOne(clamp(localScore100, 0, 100));

        Map<String, BigDecimal> scoreMap = buildScoreDetailMap(results);

        String strengths = pickDimensionText(scoreMap, true, 3);
        String weaknesses = pickDimensionText(scoreMap, false, 3);

        String levelText = buildFinalLevelText(finalScore);
        String calibrationText = buildCalibrationText(localScore, finalScore);
        String usageText = buildUsageText(finalScore);
        String actionText = buildActionText(finalScore, weaknesses);

        // 防止重复插入
        comments.removeIf(c -> c != null &&
                (c.startsWith("【最终专业评审结论】") || c.startsWith("【统一评审结论】")));

        allSuggestions.removeIf(s -> s != null &&
                (s.startsWith("【最终处理建议】") || s.startsWith("【统一处理建议】")));

        String finalConclusion = String.format(
                "系统综合本地14项评分器、NIMA/OpenCV量化结果、豆包复核与DeepSeek专业评审后判断：这张照片%s。最终综合评分为 %.1f 分，本地量化分为 %.1f 分，%s。主要优势集中在：%s；主要短板集中在：%s。%s",
                levelText,
                finalScore,
                localScore,
                calibrationText,
                strengths,
                weaknesses,
                usageText
        );

        comments.add(0, "【最终专业评审结论】" + finalConclusion);
        allSuggestions.add(0, "【最终处理建议】" + actionText);
    }

    private String pickDimensionText(Map<String, BigDecimal> scoreMap,
                                     boolean high,
                                     int limit) {
        if (scoreMap == null || scoreMap.isEmpty()) {
            return "暂无明确维度";
        }

        List<Map.Entry<String, BigDecimal>> entries = new ArrayList<>(scoreMap.entrySet());

        entries.sort((e1, e2) -> {
            BigDecimal v1 = e1.getValue() == null ? BigDecimal.ZERO : e1.getValue();
            BigDecimal v2 = e2.getValue() == null ? BigDecimal.ZERO : e2.getValue();

            return high ? v2.compareTo(v1) : v1.compareTo(v2);
        });

        StringBuilder sb = new StringBuilder();

        int count = 0;
        for (Map.Entry<String, BigDecimal> entry : entries) {
            if (count >= limit) {
                break;
            }

            String name = cleanDimensionName(entry.getKey());
            BigDecimal value = entry.getValue() == null ? BigDecimal.ZERO : entry.getValue();

            if (count > 0) {
                sb.append("、");
            }

            sb.append(name)
                    .append(" ")
                    .append(value.setScale(1, RoundingMode.HALF_UP).toPlainString())
                    .append("分");

            count++;
        }

        return sb.length() == 0 ? "暂无明确维度" : sb.toString();
    }

    private String cleanDimensionName(String name) {
        if (name == null || name.isBlank()) {
            return "未知维度";
        }

        return name
                .replace("评分", "")
                .replace("控制", "")
                .replace("表现", "")
                .trim();
    }

    private String buildActionText(double finalScore, String weaknesses) {
        if (finalScore >= 80) {
            return "建议保留为高质量候选照片。后期处理以微调为主，重点检查白平衡、局部对比、主体锐化和边缘干扰元素，避免过度修图破坏原始质感。";
        }

        if (finalScore >= 70) {
            return "建议保留并进行针对性修图。优先处理：" + weaknesses + "。后期重点应放在裁剪构图、提升局部层次、适度锐化、校正色彩和减少干扰元素上。";
        }

        if (finalScore >= 60) {
            return "建议作为待修素材保留。优先修正：" + weaknesses + "。如果修图后仍无法改善清晰度、分辨率或主体表达，则不建议作为最终精选。";
        }

        return "建议谨慎保留。当前主要问题集中在：" + weaknesses + "。除非照片具有特殊记录意义，否则更适合作为参考素材，不建议投入较多后期成本。";
    }

    private String buildUsageText(double finalScore) {
        if (finalScore >= 85) {
            return "适合作为精选作品、作品集候选、社交媒体主图或正式展示素材。";
        }

        if (finalScore >= 75) {
            return "适合作为主图候选、社交发布、活动记录或修图后展示素材。";
        }

        if (finalScore >= 70) {
            return "适合保留并进行后期优化，可用于普通展示、社交配图或项目备选素材。";
        }

        if (finalScore >= 60) {
            return "更适合作为记录留存、情绪配图或待修素材，不建议直接用于商业输出、打印放大或正式参赛。";
        }

        return "更适合作为参考记录，不建议作为正式展示、商业素材或精选照片使用。";
    }

    private String buildCalibrationText(double localScore, double finalScore) {
        double diff = roundOne(finalScore - localScore);

        if (Math.abs(diff) < 3) {
            return "多方评审结果基本一致，说明本地量化评分与豆包判断较为接近";
        }

        if (diff >= 3) {
            return "豆包复核与DeepSeek专业评审认为本地评分偏严，照片的实际观感、情绪表达或使用价值高于纯量化结果";
        }

        return "豆包复核与DeepSeek专业评审认为本地评分偏宽松，照片的实际完成度或专业用途受限，需要谨慎使用";
    }

    private String buildFinalLevelText(double finalScore) {
        if (finalScore >= 85) {
            return "具备较高完成度和精选价值，画面质量、表达和可用性都比较突出";
        }

        if (finalScore >= 75) {
            return "整体表现良好，具备明确保留价值，适合进一步修图优化后使用";
        }

        if (finalScore >= 70) {
            return "达到推荐保留水平，画面有一定优势，但仍需要通过后期提升完成度";
        }

        if (finalScore >= 60) {
            return "处于及格到待修之间，更适合作为记录素材或情绪素材保留，不建议直接作为高质量成片使用";
        }

        return "整体完成度偏弱，技术或表达短板较明显，除非有特殊记录意义，否则不建议作为最终精选";
    }

    private void appendDeepSeekPhotoReport(String originalFilename,
                                           String sceneCategory,
                                           BigDecimal finalScore,
                                           BigDecimal localScore,
                                           BigDecimal technicalScore,
                                           BigDecimal aestheticScore,
                                           BigDecimal comprehensiveScore,
                                           List<ScoringResult> results,
                                           List<String> comments,
                                           List<String> allSuggestions) {
        try {
            if (deepSeekAiService == null || !deepSeekAiService.isEnabled()) {
                return;
            }

            DeepSeekPhotoReviewResult result = deepSeekAiService.generatePhotoReview(
                    DeepSeekPhotoReviewRequest.builder()
                            .fileName(originalFilename)
                            .sceneCategory(sceneCategory)
                            .finalScore(finalScore)
                            .localScore(localScore)
                            .technicalScore(technicalScore)
                            .aestheticScore(aestheticScore)
                            .comprehensiveScore(comprehensiveScore)
                            .scoreDetails(buildScoreDetailMap(results))
                            .existingComments(comments)
                            .existingSuggestions(allSuggestions)
                            .build()
            );

            if (result == null || !result.isSuccess()) {
                log.debug("DeepSeek 照片专业报告未生成: {}", result == null ? "null" : result.getErrorMessage());
                return;
            }

            if (result.getSummary() != null && !result.getSummary().isBlank()) {
                comments.add("【DeepSeek:照片专业摘要】" + result.getSummary());
            }
            if (result.getProfessionalReview() != null && !result.getProfessionalReview().isBlank()) {
                comments.add("【DeepSeek:照片专业综合评审】" + result.getProfessionalReview());
            }
            if (result.getStrengths() != null && !result.getStrengths().isBlank()) {
                comments.add("【DeepSeek:照片优点分析】" + result.getStrengths());
            }
            if (result.getWeaknesses() != null && !result.getWeaknesses().isBlank()) {
                comments.add("【DeepSeek:照片问题分析】" + result.getWeaknesses());
            }

            if (result.getRetouchPlan() != null && !result.getRetouchPlan().isBlank()) {
                allSuggestions.add("【DeepSeek:照片专业后期方案】" + result.getRetouchPlan());
            }
            if (result.getUsageAdvice() != null && !result.getUsageAdvice().isBlank()) {
                allSuggestions.add("【DeepSeek:照片适合用途】" + result.getUsageAdvice());
            }
            if (result.getKeepDecision() != null && !result.getKeepDecision().isBlank()) {
                allSuggestions.add("【DeepSeek:照片保留建议】" + result.getKeepDecision());
            }

        } catch (Exception e) {
            log.warn("DeepSeek 照片专业报告生成异常，已跳过，不影响评分: {}", e.getMessage(), e);
        }
    }

    /**
     * 根据所有评分器的得分，自动挑出最需要改进的3个维度
     * 并生成针对性的操作建议
     */
    private String generateFinalSuggestion(List<ScoringResult> results) {
        // 1. 找出得分最低的几个维度
        List<ScoringResult> sorted = results.stream()
                .sorted(Comparator.comparing(ScoringResult::getScore))
                .collect(Collectors.toList());

        // 取最差的3个维度
        List<ScoringResult> weakest = sorted.subList(0, Math.min(3, sorted.size()));

        // 2. 为每个最差的维度生成针对性的操作建议
        List<String> finalTips = new ArrayList<>();
        for (ScoringResult r : weakest) {
            String name = r.getScorerName();
            double score = r.getScore().doubleValue();
            String tip = getSimpleTip(name, score);
            if (tip != null) {
                finalTips.add(tip);
            }
        }

        if (finalTips.isEmpty()) {
            return "各项表现均衡，继续保持你的拍摄习惯即可。";
        }

        return "这张照片最需要改进的是：" + String.join("；", finalTips) + "。";
    }

    /**
     * 根据维度名称和分数，返回最简单的操作建议
     */
    private String getSimpleTip(String scorerName, double score) {
        // 清晰度
        if (scorerName.contains("清晰度")) {
            if (score < 40) return "拍照时拿稳手机，点屏幕对焦，或擦干净镜头";
            else if (score < 60) return "拍照前等对焦框变绿再按快门，手别抖";
            else return null;
        }
        // 噪点
        if (scorerName.contains("噪点")) {
            if (score < 40) return "尽量在光线充足的地方拍照，避免暗光环境";
            else if (score < 60) return "打开手机夜景模式，或稍微调亮环境光线";
            else return null;
        }
        // 曝光
        if (scorerName.contains("曝光")) {
            if (score < 40) return "点击屏幕对焦后，滑动小太阳图标调整亮度";
            else if (score < 60) return "尝试打开HDR模式，或换个测光点";
            else return null;
        }
        // 色彩
        if (scorerName.contains("色彩")) {
            if (score < 40) return "检查是否开了特效滤镜，关闭后用原相机拍";
            else if (score < 60) return "在手机相册编辑里调整色温，让颜色更自然";
            else return null;
        }
        // 构图
        if (scorerName.contains("构图")) {
            if (score < 40) return "打开相机设置里的网格线，把主体放在交叉点上";
            else if (score < 60) return "换个角度避开背景杂物，或用人像模式虚化背景";
            else return null;
        }
        // 影调
        if (scorerName.contains("影调")) {
            if (score < 40) return "尽量在侧光环境下拍，让物体有明暗立体感";
            else if (score < 60) return "在相册编辑里加点对比度，画面会更有层次";
            else return null;
        }
        // 冲击力
        if (scorerName.contains("冲击力")) {
            if (score < 40) return "走近一点拍，或从低角度仰拍，让画面更有张力";
            else if (score < 60) return "后期加点饱和度或使用鲜明滤镜";
            else return null;
        }
        // 其他维度保持简洁
        if (scorerName.contains("主题")) {
            if (score < 50) return "想清楚最想拍什么，靠近它，让它占满画面";
            else return null;
        }
        if (scorerName.contains("瞬间")) {
            if (score < 50) return "按住快门连拍，再从里面挑最好的一张";
            else return null;
        }
        if (scorerName.contains("风格")) {
            if (score < 50) return "确定一个喜欢的滤镜，以后拍照都用它，形成统一风格";
            else return null;
        }
        // 分辨率、难度、社会价值等不需要给操作建议
        return null;
    }

    /**
     * 缩放图像，长边限制为 maxSize
     */
    private BufferedImage resizeImage(BufferedImage original, int maxSize) {
        int width = original.getWidth();
        int height = original.getHeight();

        int imageType = original.getType();
        if (imageType == BufferedImage.TYPE_CUSTOM || imageType == 0) {
            imageType = BufferedImage.TYPE_INT_RGB;
        }

        if (width <= maxSize && height <= maxSize) {
            if (original.getType() == BufferedImage.TYPE_CUSTOM || original.getType() == 0) {
                BufferedImage rgb = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                java.awt.Graphics2D g = rgb.createGraphics();
                try {
                    g.setColor(java.awt.Color.WHITE);
                    g.fillRect(0, 0, width, height);
                    g.drawImage(original, 0, 0, null);
                } finally {
                    g.dispose();
                }
                return rgb;
            }

            return original;
        }

        double ratio = (double) width / height;
        int newWidth;
        int newHeight;

        if (width > height) {
            newWidth = maxSize;
            newHeight = (int) (maxSize / ratio);
        } else {
            newHeight = maxSize;
            newWidth = (int) (maxSize * ratio);
        }

        BufferedImage resized = new BufferedImage(newWidth, newHeight, imageType);
        java.awt.Graphics2D g = resized.createGraphics();

        try {
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, newWidth, newHeight);
            g.drawImage(original, 0, 0, newWidth, newHeight, null);
        } finally {
            g.dispose();
        }

        return resized;
    }

    private PhotoScoreResponse buildResponse(CompositeScoreResult result, MultipartFile file) {
        Map<String, BigDecimal> details = new LinkedHashMap<>();
        if (result.getScoringResults() != null) {
            result.getScoringResults().forEach(r -> details.put(r.getScorerName(), r.getScore()));
        }
        return PhotoScoreResponse.builder()
                .id(result.getId())
                .fileName(file.getOriginalFilename())
                .fileSize(file.getSize())
                .dimension(result.getImageWidth() + "x" + result.getImageHeight())
                .qualityScore(result.getTotalScore())
                .isPass(result.getTotalScore().compareTo(passLine) >= 0)
                .scoreDetails(details)
                .scoreReasons(result.getComments())
                .improvementSuggestions(result.getSuggestions())
                .isDuplicate(false)
                .imagePath(result.getImagePath())
                .sceneCategory(result.getSceneCategory())
                .build();
    }

    private double calcCategoryScore(List<ScoringResult> results, String category) {
        double sum = 0, weightSum = 0;
        for (ScoringResult r : results) {
            if (category.equals(r.getCategory())) {
                BaseScorer scorer = scorers.stream()
                        .filter(s -> s.getScorerName().equals(r.getScorerName()))
                        .findFirst().orElse(null);
                if (scorer != null) {
                    double raw = r.getRawScore() != null ? r.getRawScore() : r.getScore().doubleValue() / 100.0;
                    sum += raw * scorer.getWeight();
                    weightSum += scorer.getWeight();
                }
            }
        }
        return weightSum > 0 ? sum / weightSum : 0.5;
    }

    private BigDecimal toBigDecimal(double value) {
        return BigDecimal.valueOf(value * 100).setScale(2, RoundingMode.HALF_UP);
    }
    private BigDecimal scoreToBigDecimal(double value) {
        return BigDecimal.valueOf(clamp(value, 0, 100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private String generateOverallComment(double total, String sceneCategory) {
        String baseComment;
        if (total >= 0.85) baseComment = "这是一张卓越的照片！";
        else if (total >= 0.70) baseComment = "照片质量优秀，具备良好的技术和艺术水准。";
        else if (total >= 0.55) baseComment = "照片质量良好，达到及格标准，部分维度有提升空间。";
        else if (total >= 0.40) baseComment = "照片质量一般，在技术或艺术表现方面存在不足。";
        else baseComment = "照片质量较低，建议从基础技术开始改进。";

        // 根据场景添加针对性建议
        String sceneTip = switch (sceneCategory) {
            case "自然风景" -> "拍摄自然风景时，可以尝试使用渐变滤镜平衡天空和地面的曝光，或者等待黄金时刻获取最佳光线。";
            case "城市建筑" -> "拍摄建筑时，保持垂直线的平直非常重要，可以打开参考线辅助；逆光时适当提亮暗部。";
            case "室内场景" -> "室内光线不足是常见问题，尽量靠近窗户使用自然光，或适当提高ISO但注意噪点。";
            case "人物特写" -> "人像拍摄宜使用大光圈虚化背景，对焦于眼睛，并注意柔和的自然光。";
            case "夜景" -> "夜景拍摄建议使用三脚架或稳定的支撑，降低ISO保证画质，长曝光可获得流光溢彩的效果。";
            default -> "";
        };

        return "【场景: " + sceneCategory + "】" + baseComment + " " + sceneTip;
    }

    private String getFileExtension(String name) {
        int i = name.lastIndexOf('.');
        return i > 0 ? name.substring(i + 1).toUpperCase() : "UNKNOWN";
    }
    // 在 performFullScoring 方法中，构建 record 之前，添加一个辅助方法
    private BigDecimal getScoreByName(List<ScoringResult> results, String scorerName) {
        return results.stream()
                .filter(r -> r.getScorerName().equals(scorerName))
                .findFirst()
                .map(ScoringResult::getScore)
                .orElse(null);
    }

    private Map<String, BigDecimal> buildScoreDetailMap(List<ScoringResult> results) {
        Map<String, BigDecimal> map = new LinkedHashMap<>();

        if (results == null) {
            return map;
        }

        for (ScoringResult result : results) {
            if (result != null && result.getScorerName() != null && result.getScore() != null) {
                map.put(result.getScorerName(), result.getScore());
            }
        }

        return map;
    }
    private double applyDoubaoVisionReviewAndReturnFinalScore(String originalFilename,
                                                              String sceneCategory,
                                                              double localScore,
                                                              double technicalScore,
                                                              double aestheticScore,
                                                              double comprehensiveScore,
                                                              List<ScoringResult> results,
                                                              String overallComment,
                                                              String finalSuggestion,
                                                              BufferedImage processedImage,
                                                              List<String> comments,
                                                              List<String> allSuggestions) {
        double safeLocalScore = roundOne(normalizeScoreTo100(localScore));
        double finalScore = safeLocalScore;

        try {
            DoubaoVisionReviewResult doubaoResult = doubaoVisionAiService.generateVisionReview(
                    DoubaoVisionReviewRequest.builder()
                            .fileName(originalFilename)
                            .sceneCategory(sceneCategory)
                            .localScore(scoreToBigDecimal(safeLocalScore))
                            .technicalScore(toBigDecimal(technicalScore))
                            .aestheticScore(toBigDecimal(aestheticScore))
                            .comprehensiveScore(toBigDecimal(comprehensiveScore))
                            .scoreDetails(buildScoreDetailMap(results))
                            .localOverallComment(overallComment)
                            .localFinalSuggestion(finalSuggestion)
                            .build(),
                    processedImage
            );

            if (doubaoResult != null && doubaoResult.isSuccess()) {
                finalScore = calculateFinalFusionScore(safeLocalScore, doubaoResult);
                finalScore = roundOne(clamp(finalScore, 0, 100));

                comments.clear();
                allSuggestions.clear();

                comments.add(buildLocalScorerSummary(
                        safeLocalScore,
                        technicalScore,
                        aestheticScore,
                        comprehensiveScore,
                        results
                ));

                StringBuilder scoreLine = new StringBuilder("【最终综合评分】");

                scoreLine.append("系统最终评分为 ")
                        .append(scoreToBigDecimal(finalScore).setScale(1, RoundingMode.HALF_UP))
                        .append(" 分。");

                if (doubaoResult.getFinalScoreSuggestion() != null) {
                    scoreLine.append("豆包校准参考分为 ")
                            .append(doubaoResult.getFinalScoreSuggestion().setScale(1, RoundingMode.HALF_UP))
                            .append(" 分；");
                } else if (doubaoResult.getVisionScore() != null) {
                    scoreLine.append("豆包参考分为 ")
                            .append(doubaoResult.getVisionScore().setScale(1, RoundingMode.HALF_UP))
                            .append(" 分；");
                }

                if (doubaoResult.getScoreAdjustment() != null && !doubaoResult.getScoreAdjustment().isBlank()) {
                    scoreLine.append("豆包复核判断本地量化分数")
                            .append(doubaoResult.getScoreAdjustment())
                            .append("。");
                }

                comments.add(scoreLine.toString());

                if (doubaoResult.getSummary() != null && !doubaoResult.getSummary().isBlank()) {
                    comments.add("【豆包:照片评审摘要】" + doubaoResult.getSummary());
                }

                if (doubaoResult.getVisualReview() != null && !doubaoResult.getVisualReview().isBlank()) {
                    comments.add("【豆包:照片专业综合评审】" + doubaoResult.getVisualReview());
                }

                if (doubaoResult.getQualityRisk() != null && !doubaoResult.getQualityRisk().isBlank()) {
                    String qualityRisk = doubaoResult.getQualityRisk()
                            .replaceFirst("^视觉复核", "豆包复核")
                            .replaceFirst("^AI视觉复核", "豆包复核");
                    comments.add("【豆包:照片评分校准说明】" + qualityRisk);
                }

                comments.add("【照片评分机制说明】最终分由本地 NIMA/OpenCV、14项评分器和豆包复核共同参与生成；本地评分负责量化指标，豆包负责画面理解和审美校准。");

                if (doubaoResult.getRetouchSuggestion() != null && !doubaoResult.getRetouchSuggestion().isBlank()) {
                    allSuggestions.add("【豆包:照片专业后期建议】" + doubaoResult.getRetouchSuggestion());
                }

                if (doubaoResult.getSelectionReason() != null && !doubaoResult.getSelectionReason().isBlank()) {
                    allSuggestions.add("【豆包:照片选片建议】" + doubaoResult.getSelectionReason());
                }

            } else if (doubaoResult != null && doubaoResult.getErrorMessage() != null) {
                log.debug("豆包未生成评分校准: {}", doubaoResult.getErrorMessage());
            }
        } catch (Exception e) {
            log.warn("豆包调用异常，已跳过，继续使用本地评分: {}", e.getMessage(), e);
        }

        return roundOne(clamp(finalScore, 0, 100));
    }
    private double normalizeScoreTo100(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0;
        }

        // 你原来的 totalRaw / technicalScore / aestheticScore 通常是 0-1
        if (value >= 0 && value <= 1.5) {
            return clamp(value * 100, 0, 100);
        }

        // 豆包融合后的 finalQualityScore 通常已经是 0-100
        return clamp(value, 0, 100);
    }
    private double calculateFinalFusionScore(double localScore,
                                             DoubaoVisionReviewResult doubaoResult) {
        double safeLocalScore = clamp(localScore, 0, 100);

        BigDecimal aiScoreDecimal = doubaoResult.getFinalScoreSuggestion();

        if (aiScoreDecimal == null) {
            aiScoreDecimal = doubaoResult.getVisionScore();
        }

        if (aiScoreDecimal == null) {
            return roundOne(safeLocalScore);
        }

        double aiScore = clamp(aiScoreDecimal.doubleValue(), 0, 100);

        double confidence = 0.75;
        if (doubaoResult.getConfidence() != null) {
            confidence = clamp(doubaoResult.getConfidence().doubleValue(), 0.3, 1.0);
        }

        String adjustment = doubaoResult.getScoreAdjustment();

        double aiWeight;

        if ("偏低".equals(adjustment) || "偏高".equals(adjustment)) {
            aiWeight = 0.55 + confidence * 0.20;
        } else {
            aiWeight = 0.35 + confidence * 0.15;
        }

        aiWeight = clamp(aiWeight, 0.35, 0.75);

        double fused = safeLocalScore * (1.0 - aiWeight) + aiScore * aiWeight;

        if (safeLocalScore < 60 && aiScore >= 60 && "偏低".equals(adjustment) && confidence >= 0.80) {
            fused = Math.max(fused, Math.min(aiScore, 60.0));
        }

        if (safeLocalScore >= 60 && aiScore < 60 && "偏高".equals(adjustment) && confidence >= 0.80) {
            fused = Math.min(fused, Math.max(aiScore, 59.0));
        }

        double maxDelta = 25.0;

        if (fused > safeLocalScore + maxDelta) {
            fused = safeLocalScore + maxDelta;
        }

        if (fused < safeLocalScore - maxDelta) {
            fused = safeLocalScore - maxDelta;
        }

        return roundOne(clamp(fused, 0, 100));
    }
    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private double roundOne(double value) {
        return BigDecimal.valueOf(value)
                .setScale(1, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private String buildLocalScorerSummary(double totalRaw,
                                           double technicalScore,
                                           double aestheticScore,
                                           double comprehensiveScore,
                                           List<ScoringResult> results) {
        StringBuilder sb = new StringBuilder();

        sb.append("【本地算法摘要】");

        String level;
        if (totalRaw >= 85) {
            level = "优秀，具备较高的成片质量";
        } else if (totalRaw >= 75) {
            level = "良好，具备较好的保留和展示价值";
        } else if (totalRaw >= 60) {
            level = "可用，但仍存在优化空间";
        } else {
            level = "偏弱，需要结合AI视觉复核判断实际用途";
        }

        sb.append("系统已从画质、曝光、色彩、构图、主题表达、瞬间捕捉、冲击力和记录价值等维度完成量化评估。")
                .append("本地量化判断为：")
                .append(level)
                .append("。");

        if (results == null || results.isEmpty()) {
            return sb.toString();
        }

        List<ScoringResult> validResults = results.stream()
                .filter(r -> r != null && r.getScore() != null && r.getScorerName() != null)
                .toList();

        if (validResults.isEmpty()) {
            return sb.toString();
        }

        List<String> lowNames = validResults.stream()
                .sorted(Comparator.comparing(ScoringResult::getScore))
                .limit(4)
                .map(r -> toUserFriendlyDimensionName(r.getScorerName()))
                .distinct()
                .toList();

        List<String> highNames = validResults.stream()
                .sorted(Comparator.comparing(ScoringResult::getScore).reversed())
                .limit(3)
                .map(r -> toUserFriendlyDimensionName(r.getScorerName()))
                .distinct()
                .toList();

        if (!lowNames.isEmpty()) {
            sb.append("本地算法检测到主要风险集中在")
                    .append(joinChineseList(lowNames))
                    .append("。");
        }

        if (!highNames.isEmpty()) {
            sb.append("优势主要体现在")
                    .append(joinChineseList(highNames))
                    .append("。");
        }

        sb.append("这些量化结果会参与最终综合分、雷达图、照片排序、照片对比和历史评分。");

        return sb.toString();
    }
    private String toUserFriendlyDimensionName(String scorerName) {
        if (scorerName == null) {
            return "综合表现";
        }

        String name = scorerName
                .replace("评分", "")
                .replace("控制", "")
                .replace("水平", "")
                .replace("表现", "")
                .trim();

        return switch (name) {
            case "清晰度" -> "画面清晰度";
            case "噪点" -> "画面纯净度";
            case "曝光" -> "曝光控制";
            case "色彩准确度" -> "色彩还原";
            case "影调" -> "明暗层次";
            case "分辨率" -> "细节保留";
            case "构图" -> "构图秩序";
            case "用光与色彩" -> "光线与色彩氛围";
            case "主题表达" -> "主题表达";
            case "瞬间捕捉" -> "瞬间捕捉";
            case "冲击力" -> "视觉冲击力";
            case "风格" -> "个人风格";
            case "拍摄难度" -> "拍摄难度";
            case "社会价值" -> "记录价值";
            default -> name;
        };
    }
    private String joinChineseList(List<String> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }

        if (items.size() == 1) {
            return items.get(0);
        }

        if (items.size() == 2) {
            return items.get(0) + "和" + items.get(1);
        }

        return String.join("、", items.subList(0, items.size() - 1))
                + "和"
                + items.get(items.size() - 1);
    }

    private record CurrentScoringUser(Long id, String username) {}

    @FunctionalInterface
    private interface CheckedSupplier<T> {
        T get() throws Exception;
    }
}
