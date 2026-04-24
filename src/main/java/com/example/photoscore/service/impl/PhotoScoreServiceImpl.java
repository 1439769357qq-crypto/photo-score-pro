package com.example.photoscore.service.impl;


import com.example.photoscore.config.ProgressManager;
import com.example.photoscore.mapper.PhotoScoreRecordMapper;
import com.example.photoscore.pojo.*;
import com.example.photoscore.service.PhotoScoreService;
import com.example.photoscore.service.SceneClassifier;
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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
    public CompareResult comparePhotos(MultipartFile file1, MultipartFile file2, String clientIp, String userAgent) throws IOException {
        // 将临时文件内容缓存到内存，防止汤姆猫清理临时文件
        MultipartFile safe1 = cacheMultipartFile(file1);
        MultipartFile safe2 = cacheMultipartFile(file2);

        PhotoScoreResponse resp1 = scoreSinglePhoto(safe1, clientIp, userAgent);
        PhotoScoreResponse resp2 = scoreSinglePhoto(safe2, clientIp, userAgent);

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
            advantage = String.format("照片1整体优于照片2，总分高出 %.2f 分", diff);
        } else if (diff.compareTo(BigDecimal.ZERO) < 0) {
            advantage = String.format("照片2整体优于照片1，总分高出 %.2f 分", diff.abs());
        } else {
            advantage = "两张照片总分相同，各有千秋";
        }
        return CompareResult.builder()
                .photo1(resp1).photo2(resp2)
                .scoreDiff(diff).dimensionDiff(dimDiff)
                .advantage(advantage).build();
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
        long startTime = System.currentTimeMillis();

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

        for (BaseScorer scorer : scorers) {
            try {
                ScoringResult result = scorer.score(processedImage);
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

        // 场景分类
        String sceneCategory = sceneClassifier.classify(originalImage);
        log.debug("场景分类结果: {}", sceneCategory);
        // 生成总体评价
        String overallComment = generateOverallComment(totalRaw, sceneCategory);
        comments.add(0, overallComment);

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("照片评分完成: fileName={}, 耗时={}ms, 总分={}",
                originalFilename, elapsed, toBigDecimal(totalRaw));

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

        // ===== 生成最终总体建议（只挑最差的3个） =====
        String finalSuggestion = generateFinalSuggestion(results);
        allSuggestions.add(0, "【优先改进】" + finalSuggestion);

        // 保存记录到数据库，包含 imagePath
        PhotoScoreRecord record = PhotoScoreRecord.builder()
                .fileName(originalFilename)
                .fileHash(ImageHashUtil.calculateHash(file))
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
}