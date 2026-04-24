package com.example.photoscore.service.impl;


import com.example.photoscore.config.ProgressManager;
import com.example.photoscore.mapper.PhotoScoreRecordMapper;
import com.example.photoscore.pojo.*;
import com.example.photoscore.service.PhotoScoreService;
import com.example.photoscore.util.ImageHashUtil;
import com.example.photoscore.util.OpenCVUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PhotoScoreServiceImpl implements PhotoScoreService {

    private final PhotoScoreRecordMapper recordMapper;
    private final NIMAScorer nimaScorer;
    private final ObjectMapper objectMapper;

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

    private List<BaseScorer> scorers;
    @Qualifier("asyncExecutor")  // 指定使用我们配置的线程池
    private final Executor asyncExecutor;

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

    @Override
    public BatchScoreResponse scoreBatchPhotos(List<MultipartFile> files,
                                               String clientIp, String userAgent) {
        long startTime = System.currentTimeMillis();
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
        Iterator<Map.Entry<String, MultipartFile>> iterator = hashToFileMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, MultipartFile> entry = iterator.next();
            String hash = entry.getKey();
            MultipartFile file = entry.getValue();
            PhotoScoreRecord existingRecord = recordMapper.selectByFileHash(hash);
            if (existingRecord != null) {
                duplicateMessages.add(String.format("图片 '%s' 之前已上传过 (评分: %.2f)，已跳过",
                        file.getOriginalFilename(), existingRecord.getTotalScore()));
                iterator.remove();
            }
        }

        // ===== 第3步：并行评分（核心优化）=====
        List<CompletableFuture<PhotoScoreResponse>> futures = new ArrayList<>();
        for (MultipartFile file : hashToFileMap.values()) {
            CompletableFuture<PhotoScoreResponse> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return scoreSinglePhoto(file, clientIp, userAgent);
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
    public BatchScoreResponse scoreBatchPhotosWithProgress(List<MultipartFile> files,
                                                           String clientIp,
                                                           String userAgent,
                                                           String taskId,
                                                           ProgressManager progressManager) {
        long startTime = System.currentTimeMillis();
        log.info("批量照片评分请求(带进度): fileCount={}, taskId={}", files.size(), taskId);

        // ===== 第0步：将 MultipartFile 转换为内存安全的 CachedFile =====
        List<CachedFile> safeFiles = new ArrayList<>();
        for (MultipartFile file : files) {
            try {
                safeFiles.add(new CachedFile(file));
            } catch (IOException e) {
                log.error("读取上传文件失败: {}", file.getOriginalFilename(), e);
            }
        }

        // ===== 第1步：计算哈希，内存去重 =====
        Map<String, CachedFile> hashToFileMap = new LinkedHashMap<>();
        List<String> duplicateMessages = new ArrayList<>();

        for (CachedFile file : safeFiles) {
            try {
                String fileHash = calculateHash(file.getBytes());  // 直接计算 byte[] 的哈希
                if (hashToFileMap.containsKey(fileHash)) {
                    CachedFile existing = hashToFileMap.get(fileHash);
                    duplicateMessages.add(String.format("图片 '%s' 与 '%s' 内容相同，已跳过",
                            file.getOriginalFilename(), existing.getOriginalFilename()));
                } else {
                    hashToFileMap.put(fileHash, file);
                }
            } catch (Exception e) {
                log.error("计算文件哈希失败: fileName={}", file.getOriginalFilename(), e);
            }
        }

        // ===== 第2步：数据库去重 =====
        Iterator<Map.Entry<String, CachedFile>> iterator = hashToFileMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, CachedFile> entry = iterator.next();
            String hash = entry.getKey();
            CachedFile file = entry.getValue();
            PhotoScoreRecord existingRecord = recordMapper.selectByFileHash(hash);
            if (existingRecord != null) {
                duplicateMessages.add(String.format("图片 '%s' 之前已上传过 (评分: %.2f)，已跳过",
                        file.getOriginalFilename(), existingRecord.getTotalScore()));
                iterator.remove();
            }
        }

        // ===== 第3步：提取有效文件列表 =====
        List<CachedFile> validFiles = new ArrayList<>(hashToFileMap.values());
        int total = validFiles.size();
        if (progressManager != null && taskId != null) {
            progressManager.initTask(taskId, total);
        }

        // ===== 第4步：逐张评分 =====
        List<PhotoScoreResponse> responses = new ArrayList<>();
        int processed = 0;
        for (CachedFile file : validFiles) {
            try {
                MultipartFile multipartFile = file.toMultipartFile();
                responses.add(scoreSinglePhoto(multipartFile, clientIp, userAgent));
            } catch (Exception e) {
                log.error("评分失败: {}", file.getOriginalFilename(), e);
            }
            processed++;
            if (progressManager != null && taskId != null) {
                progressManager.updateProgress(taskId, processed);
            }
        }

        // ===== 第5步：按分数排序 =====
        responses.sort((r1, r2) -> r2.getQualityScore().compareTo(r1.getQualityScore()));

        // ===== 第6步：构建响应 =====
        long processTime = System.currentTimeMillis() - startTime;
        log.info("批量照片评分完成: totalCount={}, validCount={}, duplicateCount={}, processTime={}ms",
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

    // ========== 新增：计算 byte[] 的 SHA-256 哈希值 ==========
    private String calculateHash(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }

    // ========== 内部辅助类：缓存文件内容 ==========
    private static class CachedFile {
        private final String originalFilename;
        private final String contentType;
        private final byte[] bytes;

        public CachedFile(MultipartFile file) throws IOException {
            this.originalFilename = file.getOriginalFilename();
            this.contentType = file.getContentType();
            this.bytes = file.getBytes();
        }

        public String getOriginalFilename() { return originalFilename; }
        public byte[] getBytes() { return bytes; }

        // 转换回 MultipartFile（无需任何外部依赖）
        public MultipartFile toMultipartFile() {
            return new MultipartFile() {
                @Override public String getName() { return originalFilename; }
                @Override public String getOriginalFilename() { return originalFilename; }
                @Override public String getContentType() { return contentType; }
                @Override public boolean isEmpty() { return bytes.length == 0; }
                @Override public long getSize() { return bytes.length; }
                @Override public byte[] getBytes() { return bytes; }
                @Override public InputStream getInputStream() { return new ByteArrayInputStream(bytes); }
                @Override public void transferTo(File dest) throws IOException {
                    Files.write(dest.toPath(), bytes);
                }
            };
        }
    }


    @Override
    public CompareResult comparePhotos(MultipartFile file1, MultipartFile file2, String clientIp, String userAgent) {
        PhotoScoreResponse resp1 = scoreSinglePhoto(file1, clientIp, userAgent);
        PhotoScoreResponse resp2 = scoreSinglePhoto(file2, clientIp, userAgent);

        BigDecimal diff = resp1.getQualityScore().subtract(resp2.getQualityScore());

        Map<String, BigDecimal> dimDiff = new HashMap<>();
        if (resp1.getScoreDetails() != null && resp2.getScoreDetails() != null) {
            for (String key : resp1.getScoreDetails().keySet()) {
                BigDecimal v1 = resp1.getScoreDetails().getOrDefault(key, BigDecimal.ZERO);
                BigDecimal v2 = resp2.getScoreDetails().getOrDefault(key, BigDecimal.ZERO);
                dimDiff.put(key, v1.subtract(v2));
            }
        }

        String advantage;
        if (diff.compareTo(BigDecimal.ZERO) > 0) {
            advantage = String.format("照片1整体优于照片2，总分高出 %.2f 分", diff);
        } else if (diff.compareTo(BigDecimal.ZERO) < 0) {
            advantage = String.format("照片2整体优于照片1，总分高出 %.2f 分", diff.abs());
        } else {
            advantage = "两张照片总分相同，各有千秋";
        }

        return CompareResult.builder()
                .photo1(resp1)
                .photo2(resp2)
                .scoreDiff(diff)
                .dimensionDiff(dimDiff)
                .advantage(advantage)
                .build();
    }

    /**
     * 执行完整评分，包含图像缩放优化
     */
    public CompositeScoreResult performFullScoring(MultipartFile file, String clientIp, String userAgent) throws IOException {
        long startTime = System.currentTimeMillis();

        BufferedImage originalImage = ImageIO.read(file.getInputStream());
        if (originalImage == null) throw new IOException("无法解析图片文件");

        // 缩放图像以加速评分
        BufferedImage processedImage = resizeImage(originalImage, 1200);
        OpenCVUtil.init();

        // 执行所有评分器，同时收集评语和建议
        List<ScoringResult> results = new ArrayList<>();
        List<String> comments = new ArrayList<>();
        List<String> allSuggestions = new ArrayList<>();

        for (BaseScorer scorer : scorers) {
            try {
                ScoringResult result = scorer.score(processedImage);
                results.add(result);
                // 收集评语
                if (result.getComment() != null) {
                    comments.add("【" + result.getScorerName() + "】" + result.getComment());
                }
                // 收集改进建议
                if (result.getSuggestions() != null) {
                    allSuggestions.addAll(result.getSuggestions());
                }
            } catch (Exception e) {
                log.error("评分器 {} 失败: {}", scorer.getScorerName(), e.getMessage());
            }
        }

        // NIMA评分
        double nimaAesthetic = 0.65, nimaTechnical = 0.60;
        if (nimaScorer != null && nimaScorer.isAvailable()) {
            nimaAesthetic = nimaScorer.scoreAesthetic(processedImage);
            nimaTechnical = nimaScorer.scoreTechnical(processedImage);
        }

        // 计算分类得分
        double technicalScore = calcCategoryScore(results, "TECHNICAL");
        double aestheticScore = calcCategoryScore(results, "AESTHETIC");
        double comprehensiveScore = calcCategoryScore(results, "COMPREHENSIVE");
        double totalRaw = technicalScore * 0.30 + aestheticScore * 0.40 + comprehensiveScore * 0.30;

        // 生成总体评价并置顶
        String overallComment = generateOverallComment(totalRaw);
        comments.add(0, overallComment);

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("照片评分完成: fileName={}, 耗时={}ms, 总分={}",
                file.getOriginalFilename(), elapsed, toBigDecimal(totalRaw));

        CompositeScoreResult composite = CompositeScoreResult.builder()
                .totalScore(toBigDecimal(totalRaw))
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

        // 保存记录
        PhotoScoreRecord record = PhotoScoreRecord.builder()
                .fileName(file.getOriginalFilename())
                .fileHash(ImageHashUtil.calculateHash(file))
                .fileSize(file.getSize())
                .width(processedImage.getWidth())
                .height(processedImage.getHeight())
                .format(getFileExtension(file.getOriginalFilename()))
                .totalScore(composite.getTotalScore())
                .isPass(composite.getTotalScore().compareTo(passLine) >= 0 ? 1 : 0)
                .technicalScore(composite.getTechnicalScore())
                .aestheticScore(composite.getAestheticScore())
                .comprehensiveScore(composite.getComprehensiveScore())
                .nimaTechnicalScore(composite.getNimaTechnicalScore())
                .nimaAestheticScore(composite.getNimaAestheticScore())
                // 细项分数
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
                // 新增字段
                .scoreReason(scoreReasonJson)
                .improvementSuggestions(suggestionsJson)
                .clientIp(clientIp)
                .userAgent(userAgent)
                .processTimeMs((int) elapsed)
                // 时间戳
                .createdTime(LocalDateTime.now())
                .updatedTime(LocalDateTime.now())
                .build();

        recordMapper.insert(record);
        return composite;
    }

    /**
     * 缩放图像，长边限制为 maxSize
     */
    private BufferedImage resizeImage(BufferedImage original, int maxSize) {
        int width = original.getWidth();
        int height = original.getHeight();
        if (width <= maxSize && height <= maxSize) {
            return original;
        }
        double ratio = (double) width / height;
        int newWidth, newHeight;
        if (width > height) {
            newWidth = maxSize;
            newHeight = (int) (maxSize / ratio);
        } else {
            newHeight = maxSize;
            newWidth = (int) (maxSize * ratio);
        }
        BufferedImage resized = new BufferedImage(newWidth, newHeight, original.getType());
        java.awt.Graphics2D g = resized.createGraphics();
        g.drawImage(original, 0, 0, newWidth, newHeight, null);
        g.dispose();
        return resized;
    }

    private PhotoScoreResponse buildResponse(CompositeScoreResult result, MultipartFile file) {
        Map<String, BigDecimal> details = new LinkedHashMap<>();
        if (result.getScoringResults() != null) {
            result.getScoringResults().forEach(r -> details.put(r.getScorerName(), r.getScore()));
        }
        return PhotoScoreResponse.builder()
                .fileName(file.getOriginalFilename())
                .fileSize(file.getSize())
                .dimension(result.getImageWidth() + "x" + result.getImageHeight())
                .qualityScore(result.getTotalScore())
                .isPass(result.getTotalScore().compareTo(passLine) >= 0)
                .scoreDetails(details)
                .scoreReasons(result.getComments())
                .improvementSuggestions(result.getSuggestions())
                .isDuplicate(false)
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

    private String generateOverallComment(double total) {
        if (total >= 0.85) return "【总体评价】这是一张具有国际大赛水准的卓越照片！";
        else if (total >= 0.70) return "【总体评价】照片质量优秀，具备良好的技术和艺术水准。";
        else if (total >= 0.55) return "【总体评价】照片质量良好，达到及格标准，部分维度有提升空间。";
        else if (total >= 0.40) return "【总体评价】照片质量一般，在技术或艺术表现方面存在不足。";
        else return "【总体评价】照片质量较低，建议从基础技术开始改进。";
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
}