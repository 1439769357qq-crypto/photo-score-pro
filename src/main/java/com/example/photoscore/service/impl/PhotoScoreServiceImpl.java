package com.example.photoscore.service.impl;


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
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
            CompositeScoreResult result = performFullScoring(file);
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

    /**
     * 执行完整评分，包含图像缩放优化
     */
    public CompositeScoreResult performFullScoring(MultipartFile file) throws IOException {
        long startTime = System.currentTimeMillis();

        // 1. 读取原始图像
        BufferedImage originalImage = ImageIO.read(file.getInputStream());
        if (originalImage == null) throw new IOException("无法解析图片文件");

        // 2. 缩放图像以加速评分（长边限制为1200px）
        BufferedImage processedImage = resizeImage(originalImage, 1200);

        // 3. 初始化OpenCV
        OpenCVUtil.init();

        // 4. 执行所有评分器
        List<ScoringResult> results = new ArrayList<>();
        for (BaseScorer scorer : scorers) {
            try {
                ScoringResult result = scorer.score(processedImage);
                results.add(result);
            } catch (Exception e) {
                log.error("评分器 {} 失败: {}", scorer.getScorerName(), e.getMessage());
            }
        }

        // 5. NIMA评分（降级或正常）
        double nimaAesthetic = 0.65, nimaTechnical = 0.60;
        if (nimaScorer != null && nimaScorer.isAvailable()) {
            nimaAesthetic = nimaScorer.scoreAesthetic(processedImage);
            nimaTechnical = nimaScorer.scoreTechnical(processedImage);
        }

        // 6. 计算分类得分
        double technicalScore = calcCategoryScore(results, "TECHNICAL");
        double aestheticScore = calcCategoryScore(results, "AESTHETIC");
        double comprehensiveScore = calcCategoryScore(results, "COMPREHENSIVE");
        double totalRaw = technicalScore * 0.30 + aestheticScore * 0.40 + comprehensiveScore * 0.30;

        // 7. 收集评语
        List<String> comments = results.stream()
                .map(ScoringResult::getComment)
                .collect(Collectors.toList());
        comments.add(0, generateOverallComment(totalRaw));

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
                .imageWidth(processedImage.getWidth())
                .imageHeight(processedImage.getHeight())
                .elapsedMs(elapsed)
                .build();

        // 8. 保存记录
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
}