package com.example.photoscore.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import com.example.photoscore.dto.WorkspaceDtos;
import com.example.photoscore.mapper.*;
import com.example.photoscore.pojo.*;
import com.example.photoscore.security.WorkspaceCurrentUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WorkspaceService {
    private final ProjectMapper projectMapper;
    private final ManualReviewMapper manualReviewMapper;
    private final CollectionItemMapper collectionItemMapper;
    private final UsageEventMapper usageEventMapper;
    private final ExifRecordMapper exifRecordMapper;
    private final WorkspaceCurrentUser currentUser;
    private final ObjectMapper objectMapper;

    public List<ProjectEntity> listProjects() {
        Long userId = currentUser.userId();
        return projectMapper.selectList(new LambdaQueryWrapper<ProjectEntity>()
                .eq(ProjectEntity::getUserId, userId)
                .orderByDesc(ProjectEntity::getUpdatedAt)
                .orderByDesc(ProjectEntity::getId));
    }

    @Transactional
    public ProjectEntity createProject(WorkspaceDtos.ProjectRequest req) {
        Long userId = currentUser.userId();
        if (blank(req.getProjectName())) throw new RuntimeException("项目名称不能为空");

        ProjectEntity e = new ProjectEntity();
        e.setUserId(userId);
        fillProject(e, req);
        projectMapper.insert(e);
        recordUsage("PROJECT_SAVE", 1, e.getId(), null, null);
        return e;
    }

    @Transactional
    public ProjectEntity updateProject(Long id, WorkspaceDtos.ProjectRequest req) {
        Long userId = currentUser.userId();
        ProjectEntity e = projectMapper.selectOne(new LambdaQueryWrapper<ProjectEntity>()
                .eq(ProjectEntity::getId, id)
                .eq(ProjectEntity::getUserId, userId));
        if (e == null) throw new RuntimeException("项目不存在或无权访问");

        fillProject(e, req);
        projectMapper.updateById(e);
        recordUsage("PROJECT_SAVE", 1, id, null, null);
        return projectMapper.selectById(id);
    }

    @Transactional
    public void deleteProject(Long id) {
        Long userId = currentUser.userId();
        int rows = projectMapper.delete(new LambdaQueryWrapper<ProjectEntity>()
                .eq(ProjectEntity::getId, id)
                .eq(ProjectEntity::getUserId, userId));
        if (rows == 0) throw new RuntimeException("项目不存在或无权删除");
    }

    private void fillProject(ProjectEntity e, WorkspaceDtos.ProjectRequest r) {
        if (!blank(r.getProjectName())) e.setProjectName(r.getProjectName().trim());
        e.setClientName(trim(r.getClientName()));
        e.setShootType(trim(r.getShootType()));
        e.setShootDate(r.getShootDate());
        e.setNote(limit(r.getNote(), 1000));
        e.setPhotoCount(nvl(r.getPhotoCount(), nvl(e.getPhotoCount(), 0)));
        e.setAvgScore(r.getAvgScore() == null ? nvlBig(e.getAvgScore()) : r.getAvgScore());
        e.setKeepCount(nvl(r.getKeepCount(), nvl(e.getKeepCount(), 0)));
        e.setRetouchCount(nvl(r.getRetouchCount(), nvl(e.getRetouchCount(), 0)));
        e.setDropCount(nvl(r.getDropCount(), nvl(e.getDropCount(), 0)));
        e.setSelectedCount(nvl(r.getSelectedCount(), nvl(e.getSelectedCount(), 0)));
    }

    public List<ManualReviewEntity> listManualReviews(Long projectId) {
        Long userId = currentUser.userId();
        LambdaQueryWrapper<ManualReviewEntity> q = new LambdaQueryWrapper<ManualReviewEntity>()
                .eq(ManualReviewEntity::getUserId, userId)
                .orderByDesc(ManualReviewEntity::getUpdatedAt)
                .orderByDesc(ManualReviewEntity::getId);
        if (projectId != null) q.eq(ManualReviewEntity::getProjectId, projectId);
        return manualReviewMapper.selectList(q);
    }

    public ManualReviewEntity oneManualReview(String photoKey) {
        Long userId = currentUser.userId();
        if (blank(photoKey)) throw new RuntimeException("photoKey 不能为空");
        return manualReviewMapper.selectOne(new LambdaQueryWrapper<ManualReviewEntity>()
                .eq(ManualReviewEntity::getUserId, userId)
                .eq(ManualReviewEntity::getPhotoKey, photoKey));
    }

    @Transactional
    public ManualReviewEntity upsertManualReview(WorkspaceDtos.ManualReviewRequest r) {
        Long userId = currentUser.userId();
        if (blank(r.getPhotoKey())) throw new RuntimeException("photoKey 不能为空");
        if (blank(r.getFileName())) throw new RuntimeException("文件名不能为空");

        ManualReviewEntity e = oneManualReview(r.getPhotoKey());
        boolean create = e == null;
        if (create) {
            e = new ManualReviewEntity();
            e.setUserId(userId);
            e.setPhotoKey(r.getPhotoKey().trim());
        }

        e.setProjectId(r.getProjectId());
        e.setFileName(r.getFileName().trim());
        e.setManualStatus(normalizeManualStatus(r.getManualStatus()));
        e.setManualScore(r.getManualScore());
        e.setManualNote(limit(r.getManualNote(), 1200));
        e.setOverrideAi(Boolean.TRUE.equals(r.getOverrideAi()));
        e.setSelected(Boolean.TRUE.equals(r.getSelected()));
        e.setIssueTags(limit(r.getIssueTags(), 1000));

        if (create) manualReviewMapper.insert(e); else manualReviewMapper.updateById(e);

        recordUsage("MANUAL_REVIEW", 1, r.getProjectId(), r.getFileName(), null);
        return oneManualReview(r.getPhotoKey());
    }

    @Transactional
    public void deleteManualReview(String photoKey) {
        Long userId = currentUser.userId();
        if (blank(photoKey)) throw new RuntimeException("photoKey 不能为空");
        manualReviewMapper.delete(new LambdaQueryWrapper<ManualReviewEntity>()
                .eq(ManualReviewEntity::getUserId, userId)
                .eq(ManualReviewEntity::getPhotoKey, photoKey));
    }

    public List<CollectionItemEntity> listCollections(Long projectId) {
        Long userId = currentUser.userId();
        LambdaQueryWrapper<CollectionItemEntity> q = new LambdaQueryWrapper<CollectionItemEntity>()
                .eq(CollectionItemEntity::getUserId, userId)
                .orderByDesc(CollectionItemEntity::getCreatedAt)
                .orderByDesc(CollectionItemEntity::getId);
        if (projectId != null) q.eq(CollectionItemEntity::getProjectId, projectId);
        return collectionItemMapper.selectList(q);
    }

    @Transactional
    public CollectionItemEntity addCollection(WorkspaceDtos.CollectionItemRequest r) {
        Long userId = currentUser.userId();
        if (blank(r.getPhotoKey())) throw new RuntimeException("photoKey 不能为空");
        if (blank(r.getFileName())) throw new RuntimeException("文件名不能为空");

        CollectionItemEntity e = collectionItemMapper.selectOne(new LambdaQueryWrapper<CollectionItemEntity>()
                .eq(CollectionItemEntity::getUserId, userId)
                .eq(CollectionItemEntity::getPhotoKey, r.getPhotoKey()));
        boolean create = e == null;
        if (create) {
            e = new CollectionItemEntity();
            e.setUserId(userId);
            e.setPhotoKey(r.getPhotoKey().trim());
        }

        e.setProjectId(r.getProjectId());
        e.setFileName(r.getFileName().trim());
        e.setOriginalScore(r.getOriginalScore());
        e.setFinalScore(r.getFinalScore());
        e.setFinalStatus(trim(r.getFinalStatus()));
        e.setNote(limit(r.getNote(), 1200));

        if (create) collectionItemMapper.insert(e); else collectionItemMapper.updateById(e);
        recordUsage("COLLECTION_ADD", 1, r.getProjectId(), r.getFileName(), null);

        return collectionItemMapper.selectOne(new LambdaQueryWrapper<CollectionItemEntity>()
                .eq(CollectionItemEntity::getUserId, userId)
                .eq(CollectionItemEntity::getPhotoKey, r.getPhotoKey()));
    }

    @Transactional
    public void deleteCollection(String photoKey) {
        Long userId = currentUser.userId();
        if (blank(photoKey)) throw new RuntimeException("photoKey 不能为空");
        collectionItemMapper.delete(new LambdaQueryWrapper<CollectionItemEntity>()
                .eq(CollectionItemEntity::getUserId, userId)
                .eq(CollectionItemEntity::getPhotoKey, photoKey));
        recordUsage("COLLECTION_REMOVE", 1, null, null, null);
    }

    public byte[] exportCollectionsCsv(Long projectId) {
        List<CollectionItemEntity> list = listCollections(projectId);
        StringBuilder sb = new StringBuilder("\uFEFF");
        sb.append("序号,文件名,原始分,最终分,最终状态,备注,加入时间\n");
        int i = 1;
        for (CollectionItemEntity x : list) {
            sb.append(i++).append(',')
                    .append(csv(x.getFileName())).append(',')
                    .append(val(x.getOriginalScore())).append(',')
                    .append(val(x.getFinalScore())).append(',')
                    .append(csv(x.getFinalStatus())).append(',')
                    .append(csv(x.getNote())).append(',')
                    .append(csv(x.getCreatedAt())).append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Transactional
    public UsageEventEntity recordUsage(WorkspaceDtos.UsageEventRequest r) {
        return recordUsage(r.getEventType(), nvl(r.getEventCount(), 1), r.getProjectId(), r.getFileName(), r.getMetaJson());
    }

    @Transactional
    public UsageEventEntity recordUsage(String eventType, Integer count, Long projectId, String fileName, String metaJson) {
        Long userId = currentUser.userId();
        UsageEventEntity e = new UsageEventEntity();
        e.setUserId(userId);
        e.setEventType(blank(eventType) ? "UNKNOWN" : eventType.trim().toUpperCase());
        e.setEventCount(count == null || count <= 0 ? 1 : count);
        e.setProjectId(projectId);
        e.setFileName(limit(fileName, 255));
        e.setMetaJson(limit(metaJson, 8000));
        usageEventMapper.insert(e);
        return e;
    }

    public WorkspaceDtos.UsageSummaryResponse usageSummary() {
        Long userId = currentUser.userId();
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();

        Map<String, Integer> today = countUsage(userId, todayStart);
        Map<String, Integer> total = countUsage(userId, null);

        UsageEventEntity last = usageEventMapper.selectOne(new LambdaQueryWrapper<UsageEventEntity>()
                .eq(UsageEventEntity::getUserId, userId)
                .orderByDesc(UsageEventEntity::getCreatedAt)
                .last("LIMIT 1"));

        int projects = projectMapper.selectCount(new LambdaQueryWrapper<ProjectEntity>().eq(ProjectEntity::getUserId, userId)).intValue();
        int manual = manualReviewMapper.selectCount(new LambdaQueryWrapper<ManualReviewEntity>().eq(ManualReviewEntity::getUserId, userId)).intValue();
        int collection = collectionItemMapper.selectCount(new LambdaQueryWrapper<CollectionItemEntity>().eq(CollectionItemEntity::getUserId, userId)).intValue();
        int exif = exifRecordMapper.selectCount(new LambdaQueryWrapper<ExifRecordEntity>().eq(ExifRecordEntity::getUserId, userId)).intValue();

        return WorkspaceDtos.UsageSummaryResponse.builder()
                .todayUploadCount(today.getOrDefault("UPLOAD", 0))
                .todayScoreCount(today.getOrDefault("SCORE", 0))
                .todayManualReviewCount(today.getOrDefault("MANUAL_REVIEW", 0))
                .todayCollectionAddCount(today.getOrDefault("COLLECTION_ADD", 0))
                .todayExifAnalyzeCount(today.getOrDefault("EXIF_ANALYZE", 0))
                .totalUploadCount(total.getOrDefault("UPLOAD", 0))
                .totalScoreCount(total.getOrDefault("SCORE", 0))
                .totalManualReviewCount(manual)
                .totalCollectionCount(collection)
                .totalProjectCount(projects)
                .totalExifCount(exif)
                .todayQuotaText("无限制")
                .lastActiveAt(last == null ? null : last.getCreatedAt())
                .todayByType(today)
                .totalByType(total)
                .build();
    }

    private Map<String, Integer> countUsage(Long userId, LocalDateTime start) {
        LambdaQueryWrapper<UsageEventEntity> q = new LambdaQueryWrapper<UsageEventEntity>().eq(UsageEventEntity::getUserId, userId);
        if (start != null) q.ge(UsageEventEntity::getCreatedAt, start);
        List<UsageEventEntity> list = usageEventMapper.selectList(q);
        Map<String, Integer> map = new LinkedHashMap<>();
        for (UsageEventEntity e : list) {
            map.put(e.getEventType(), map.getOrDefault(e.getEventType(), 0) + nvl(e.getEventCount(), 1));
        }
        return map;
    }

    @Transactional
    public ExifRecordEntity analyzeExif(MultipartFile file, Long projectId, String photoKey) {
        try {
            if (file == null || file.isEmpty()) throw new RuntimeException("请上传一张照片");
            Long userId = currentUser.userId();

            byte[] bytes = file.getBytes();
            String key = blank(photoKey) ? md5(bytes) : photoKey.trim();
            String fileName = file.getOriginalFilename() == null ? "unknown" : file.getOriginalFilename();

            Metadata metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(bytes));
            Map<String, String> raw = new LinkedHashMap<>();
            for (Directory d : metadata.getDirectories()) {
                for (Tag t : d.getTags()) {
                    raw.put(d.getName() + "." + t.getTagName(), t.getDescription());
                }
            }

            BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
            ExifIFD0Directory ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            ExifSubIFDDirectory sub = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
            GpsDirectory gps = metadata.getFirstDirectoryOfType(GpsDirectory.class);

            ExifRecordEntity e = exifRecordMapper.selectOne(new LambdaQueryWrapper<ExifRecordEntity>()
                    .eq(ExifRecordEntity::getUserId, userId)
                    .eq(ExifRecordEntity::getPhotoKey, key));
            boolean create = e == null;
            if (create) {
                e = new ExifRecordEntity();
                e.setUserId(userId);
                e.setPhotoKey(key);
            }

            e.setProjectId(projectId);
            e.setFileName(fileName);
            e.setCameraMake(ifd0 == null ? null : clean(ifd0.getString(ExifIFD0Directory.TAG_MAKE)));
            e.setCameraModel(ifd0 == null ? null : clean(ifd0.getString(ExifIFD0Directory.TAG_MODEL)));
            e.setLensModel(sub == null ? null : clean(sub.getString(ExifSubIFDDirectory.TAG_LENS_MODEL)));
            e.setFocalLength(sub == null ? null : clean(sub.getDescription(ExifSubIFDDirectory.TAG_FOCAL_LENGTH)));
            e.setAperture(sub == null ? null : clean(firstNonBlank(sub.getDescription(ExifSubIFDDirectory.TAG_FNUMBER), sub.getDescription(ExifSubIFDDirectory.TAG_APERTURE))));
            e.setShutterSpeed(sub == null ? null : clean(firstNonBlank(sub.getDescription(ExifSubIFDDirectory.TAG_EXPOSURE_TIME), sub.getDescription(ExifSubIFDDirectory.TAG_SHUTTER_SPEED))));
            e.setIso(sub == null ? null : clean(sub.getDescription(ExifSubIFDDirectory.TAG_ISO_EQUIVALENT)));
            e.setExposureBias(sub == null ? null : clean(sub.getDescription(ExifSubIFDDirectory.TAG_EXPOSURE_BIAS)));
            e.setShootTime(sub == null ? null : clean(firstNonBlank(sub.getDescription(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL), sub.getDescription(ExifSubIFDDirectory.TAG_DATETIME_DIGITIZED))));
            e.setGps(gps == null || gps.getGeoLocation() == null ? null : gps.getGeoLocation().toString());
            e.setWidth(img == null ? null : img.getWidth());
            e.setHeight(img == null ? null : img.getHeight());
            e.setFileSize(file.getSize());
            e.setRawJson(objectMapper.writeValueAsString(raw));
            e.setAdvice(buildExifAdvice(e));

            if (create) exifRecordMapper.insert(e); else exifRecordMapper.updateById(e);
            recordUsage("EXIF_ANALYZE", 1, projectId, fileName, null);

            return exifRecordMapper.selectOne(new LambdaQueryWrapper<ExifRecordEntity>()
                    .eq(ExifRecordEntity::getUserId, userId)
                    .eq(ExifRecordEntity::getPhotoKey, key));
        } catch (Exception e) {
            throw new RuntimeException("EXIF 分析失败：" + e.getMessage(), e);
        }
    }

    private String buildExifAdvice(ExifRecordEntity e) {
        StringBuilder sb = new StringBuilder();
        Integer iso = parseFirstInt(e.getIso());
        Double shutter = parseShutter(e.getShutterSpeed());
        Double aperture = parseFirstDouble(e.getAperture());
        Double focal = parseFirstDouble(e.getFocalLength());

        if (iso != null) {
            if (iso >= 1600) sb.append("ISO较高，噪点风险明显，建议优先降噪。");
            else if (iso >= 800) sb.append("ISO偏高，暗部可能出现噪点。");
            else sb.append("ISO较低，画面纯净度通常更有保障。");
        }
        if (shutter != null && shutter > 1.0 / 60.0) sb.append("快门速度偏慢，手持拍摄可能产生抖动。");
        if (aperture != null && aperture <= 2.0) sb.append("大光圈景深较浅，对焦容错率低。");
        if (focal != null && focal <= 24) sb.append("广角焦段适合风光、建筑和纪实，但需注意边缘变形。");
        if (focal != null && focal >= 70) sb.append("中长焦有利于突出主体，适合人像和特写。");
        if (e.getWidth() != null && e.getHeight() != null && (long)e.getWidth() * e.getHeight() < 2_000_000L) {
            sb.append("图片像素较低，不建议大尺寸打印或高清商业输出。");
        }
        if (sb.length() == 0) sb.append("未发现明显拍摄参数风险，可结合评分结果继续判断。");
        return sb.toString();
    }

    private String normalizeManualStatus(String s) {
        if (blank(s)) return "UNSET";
        String x = s.trim().toUpperCase();
        return switch (x) {
            case "RECOMMEND", "KEEP", "RETOUCH", "DROP", "UNSET" -> x;
            default -> throw new RuntimeException("人工标记不合法，可选：RECOMMEND / RETOUCH / DROP / UNSET");
        };
    }

    private Integer parseFirstInt(String s) {
        if (blank(s)) return null;
        String x = s.replaceAll("[^0-9]", " ").trim().replaceAll("\\s+", " ");
        if (blank(x)) return null;
        try { return Integer.parseInt(x.split(" ")[0]); } catch (Exception e) { return null; }
    }

    private Double parseFirstDouble(String s) {
        if (blank(s)) return null;
        String x = s.replaceAll("[^0-9.]", " ").trim().replaceAll("\\s+", " ");
        if (blank(x)) return null;
        try { return Double.parseDouble(x.split(" ")[0]); } catch (Exception e) { return null; }
    }

    private Double parseShutter(String s) {
        if (blank(s)) return null;
        try {
            if (s.contains("/")) {
                String f = s.replaceAll("[^0-9/]", "");
                String[] p = f.split("/");
                if (p.length == 2) return Double.parseDouble(p[0]) / Double.parseDouble(p[1]);
            }
            return parseFirstDouble(s);
        } catch (Exception e) {
            return null;
        }
    }

    private String md5(byte[] bytes) {
        try {
            MessageDigest d = MessageDigest.getInstance("MD5");
            byte[] hash = d.digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("生成 photoKey 失败", e);
        }
    }

    private String csv(Object value) {
        if (value == null) return "";
        return "\"" + String.valueOf(value).replace("\"", "\"\"") + "\"";
    }

    private String val(Object value) { return value == null ? "" : String.valueOf(value); }
    private String trim(String value) { return value == null ? null : value.trim(); }
    private String clean(String value) { return blank(value) ? null : value.trim(); }
    private String limit(String value, int max) { if (value == null) return null; String t = value.trim(); return t.length() > max ? t.substring(0, max) : t; }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private Integer nvl(Integer v, Integer f) { return v == null ? f : v; }
    private BigDecimal nvlBig(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
    private String firstNonBlank(String... values) { for (String v : values) if (!blank(v)) return v; return null; }
}
