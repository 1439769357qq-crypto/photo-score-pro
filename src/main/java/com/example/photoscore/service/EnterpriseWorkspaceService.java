
package com.example.photoscore.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
public class EnterpriseWorkspaceService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final Environment environment;

    private record CurrentUser(Long id, String username, boolean admin) {}

    private CurrentUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new IllegalStateException("请先登录");
        }

        Long userId = null;
        Object details = auth.getDetails();
        if (details != null) {
            userId = readLong(details, "getUserId");
            if (userId == null) userId = readLong(details, "getUid");
            if (userId == null) userId = readLong(details, "getId");
        }
        if (userId == null) {
            Object principal = auth.getPrincipal();
            userId = readLong(principal, "getUserId");
        }
        if (userId == null) {
            String name = String.valueOf(auth.getName() == null ? "user" : auth.getName());
            userId = Math.abs((long) name.hashCode());
        }

        String username = String.valueOf(auth.getName() == null ? "user" : auth.getName());
        String admins = environment.getProperty("PHOTOSCORE_ADMIN_USERS", "admin");
        boolean admin = Arrays.stream(admins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .anyMatch(s -> s.equalsIgnoreCase(username));

        return new CurrentUser(userId, username, admin);
    }

    private Long readLong(Object obj, String methodName) {
        if (obj == null) return null;
        try {
            Method m = obj.getClass().getMethod(methodName);
            Object v = m.invoke(obj);
            if (v instanceof Number n) return n.longValue();
            if (v != null && String.valueOf(v).matches("\\d+")) return Long.parseLong(String.valueOf(v));
        } catch (Exception ignored) {}
        return null;
    }

    private String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private String str(Map<String, Object> m, String key, String def) {
        String s = str(m, key);
        return s == null || s.isBlank() ? def : s;
    }

    private Long longVal(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null || String.valueOf(v).isBlank()) return null;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(v)); } catch (Exception e) { return null; }
    }

    private Integer intVal(Map<String, Object> m, String key, int def) {
        Object v = m.get(key);
        if (v == null || String.valueOf(v).isBlank()) return def;
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(v)); } catch (Exception e) { return def; }
    }

    private BigDecimal dec(Map<String, Object> m, String key, double def) {
        Object v = m.get(key);
        if (v == null || String.valueOf(v).isBlank()) return BigDecimal.valueOf(def);
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try { return new BigDecimal(String.valueOf(v)); } catch (Exception e) { return BigDecimal.valueOf(def); }
    }

    private Boolean bool(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.intValue() != 0;
        return v != null && ("true".equalsIgnoreCase(String.valueOf(v)) || "1".equals(String.valueOf(v)) || "yes".equalsIgnoreCase(String.valueOf(v)));
    }

    private Date sqlDate(Map<String, Object> m, String key) {
        String v = str(m, key);
        if (v == null || v.isBlank()) return null;
        try { return Date.valueOf(LocalDate.parse(v)); } catch (Exception e) { return null; }
    }

    private String json(Object value) {
        if (value == null) return null;
        try { return objectMapper.writeValueAsString(value); } catch (Exception e) { return String.valueOf(value); }
    }

    private Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try { return objectMapper.readValue(json, new TypeReference<>() {}); } catch (Exception e) { return Map.of("raw", json); }
    }

    private Long insert(String sql, SqlSetter setter) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            setter.set(ps);
            return ps;
        }, kh);
        Number n = kh.getKey();
        return n == null ? null : n.longValue();
    }

    @FunctionalInterface
    private interface SqlSetter {
        void set(PreparedStatement ps) throws java.sql.SQLException;
    }

    public List<Map<String, Object>> listProjects() {
        CurrentUser u = currentUser();
        return jdbc.queryForList("""
                SELECT * FROM ps_ent_project
                WHERE user_id=?
                ORDER BY updated_at DESC, id DESC
                """, u.id());
    }

    public Map<String, Object> saveProject(Map<String, Object> body) {
        CurrentUser u = currentUser();
        Long id = longVal(body, "id");
        if (id != null) {
            int updated = jdbc.update("""
                    UPDATE ps_ent_project SET project_name=?, client_name=?, shoot_type=?, shoot_date=?,
                    status=?, note=?, cover_url=?, delivery_version=?, photo_count=?, avg_score=?,
                    selected_count=?, retouch_count=?, delivery_count=?, raw_json=?
                    WHERE id=? AND user_id=?
                    """,
                    str(body, "projectName", str(body, "name", "未命名项目")),
                    str(body, "clientName"),
                    str(body, "shootType"),
                    sqlDate(body, "shootDate"),
                    str(body, "status", "进行中"),
                    str(body, "note"),
                    str(body, "coverUrl"),
                    str(body, "deliveryVersion", str(body, "version", "v1")),
                    intVal(body, "photoCount", 0),
                    dec(body, "avgScore", 0),
                    intVal(body, "selectedCount", 0),
                    intVal(body, "retouchCount", 0),
                    intVal(body, "deliveryCount", 0),
                    json(body),
                    id, u.id());
            if (updated > 0) return projectById(id);
        }

        Long newId = insert("""
                INSERT INTO ps_ent_project(user_id, project_name, client_name, shoot_type, shoot_date, status, note,
                cover_url, delivery_version, photo_count, avg_score, selected_count, retouch_count, delivery_count, raw_json)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, ps -> {
            ps.setLong(1, u.id());
            ps.setString(2, str(body, "projectName", str(body, "name", "未命名项目")));
            ps.setString(3, str(body, "clientName"));
            ps.setString(4, str(body, "shootType"));
            ps.setDate(5, sqlDate(body, "shootDate"));
            ps.setString(6, str(body, "status", "进行中"));
            ps.setString(7, str(body, "note"));
            ps.setString(8, str(body, "coverUrl"));
            ps.setString(9, str(body, "deliveryVersion", str(body, "version", "v1")));
            ps.setInt(10, intVal(body, "photoCount", 0));
            ps.setBigDecimal(11, dec(body, "avgScore", 0));
            ps.setInt(12, intVal(body, "selectedCount", 0));
            ps.setInt(13, intVal(body, "retouchCount", 0));
            ps.setInt(14, intVal(body, "deliveryCount", 0));
            ps.setString(15, json(body));
        });
        recordUsage(Map.of("eventType", "project_saved", "eventCount", 1, "projectId", newId));
        return projectById(newId);
    }

    private Map<String, Object> projectById(Long id) {
        CurrentUser u = currentUser();
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM ps_ent_project WHERE id=? AND user_id=?", id, u.id());
        return rows.isEmpty() ? Map.of("id", id) : rows.get(0);
    }

    public void deleteProject(Long id) {
        CurrentUser u = currentUser();
        jdbc.update("DELETE FROM ps_ent_project WHERE id=? AND user_id=?", id, u.id());
        recordUsage(Map.of("eventType", "project_deleted", "eventCount", 1, "projectId", id));
    }

    public Map<String, Object> saveProjectVersion(Long projectId, Map<String, Object> body) {
        CurrentUser u = currentUser();
        Long id = insert("""
                INSERT INTO ps_ent_project_version(user_id, project_id, version_name, snapshot_json)
                VALUES(?,?,?,?)
                """, ps -> {
            ps.setLong(1, u.id());
            ps.setObject(2, projectId);
            ps.setString(3, str(body, "versionName", "snapshot"));
            ps.setString(4, json(body));
        });
        return Map.of("id", id, "projectId", projectId);
    }

    public List<Map<String, Object>> listManualReviews(Long projectId) {
        CurrentUser u = currentUser();
        if (projectId == null) {
            return jdbc.queryForList("SELECT * FROM ps_ent_manual_review WHERE user_id=? ORDER BY updated_at DESC", u.id());
        }
        return jdbc.queryForList("SELECT * FROM ps_ent_manual_review WHERE user_id=? AND project_id=? ORDER BY updated_at DESC", u.id(), projectId);
    }

    public Map<String, Object> getManualReview(String photoKey) {
        CurrentUser u = currentUser();
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM ps_ent_manual_review WHERE user_id=? AND photo_key=?", u.id(), photoKey);
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    public Map<String, Object> saveManualReview(Map<String, Object> body) {
        CurrentUser u = currentUser();
        String photoKey = str(body, "photoKey", str(body, "fileName", UUID.randomUUID().toString()));
        Map<String, Object> before = getManualReview(photoKey);
        Long existingId = before.get("id") instanceof Number n ? n.longValue() : null;

        if (existingId != null) {
            jdbc.update("""
                    UPDATE ps_ent_manual_review SET project_id=?, file_name=?, manual_status=?, manual_score=?,
                    manual_note=?, override_ai=?, selected=?, reviewer=?, issue_tags=?, raw_json=?
                    WHERE id=? AND user_id=?
                    """,
                    longVal(body, "projectId"),
                    str(body, "fileName", photoKey),
                    str(body, "manualStatus", str(body, "status", "未复核")),
                    dec(body, "manualScore", 0),
                    str(body, "manualNote", str(body, "note", "")),
                    bool(body, "overrideAi") ? 1 : 0,
                    bool(body, "selected") ? 1 : 0,
                    u.username(),
                    str(body, "issueTags"),
                    json(body),
                    existingId, u.id());
            recordReviewHistory(existingId, longVal(body, "projectId"), photoKey, "UPDATE", before, body);
        } else {
            existingId = insert("""
                    INSERT INTO ps_ent_manual_review(user_id, project_id, photo_key, file_name, manual_status,
                    manual_score, manual_note, override_ai, selected, reviewer, issue_tags, raw_json)
                    VALUES(?,?,?,?,?,?,?,?,?,?,?,?)
                    """, ps -> {
                ps.setLong(1, u.id());
                ps.setObject(2, longVal(body, "projectId"));
                ps.setString(3, photoKey);
                ps.setString(4, str(body, "fileName", photoKey));
                ps.setString(5, str(body, "manualStatus", str(body, "status", "未复核")));
                ps.setBigDecimal(6, dec(body, "manualScore", 0));
                ps.setString(7, str(body, "manualNote", str(body, "note", "")));
                ps.setInt(8, bool(body, "overrideAi") ? 1 : 0);
                ps.setInt(9, bool(body, "selected") ? 1 : 0);
                ps.setString(10, u.username());
                ps.setString(11, str(body, "issueTags"));
                ps.setString(12, json(body));
            });
            recordReviewHistory(existingId, longVal(body, "projectId"), photoKey, "CREATE", Map.of(), body);
        }
        recordUsage(Map.of("eventType", "manual_review", "eventCount", 1, "projectId", longVal(body, "projectId")));
        return getManualReview(photoKey);
    }

    public Map<String, Object> saveManualReviewBatch(Map<String, Object> body) {
        List<?> items = (List<?>) body.getOrDefault("items", List.of());
        int count = 0;
        for (Object item : items) {
            if (item instanceof Map<?, ?> raw) {
                Map<String, Object> m = new LinkedHashMap<>();
                raw.forEach((k, v) -> m.put(String.valueOf(k), v));
                saveManualReview(m);
                count++;
            }
        }
        return Map.of("count", count);
    }

    private void recordReviewHistory(Long reviewId, Long projectId, String photoKey, String action, Object before, Object after) {
        CurrentUser u = currentUser();
        jdbc.update("""
                INSERT INTO ps_ent_manual_review_history(user_id, review_id, project_id, photo_key, action_type, before_json, after_json)
                VALUES(?,?,?,?,?,?,?)
                """, u.id(), reviewId, projectId, photoKey, action, json(before), json(after));
    }

    public void deleteManualReview(String photoKey) {
        CurrentUser u = currentUser();
        Map<String, Object> before = getManualReview(photoKey);
        jdbc.update("DELETE FROM ps_ent_manual_review WHERE user_id=? AND photo_key=?", u.id(), photoKey);
        recordReviewHistory(null, null, photoKey, "DELETE", before, Map.of());
    }

    public List<Map<String, Object>> listManualReviewHistory(String photoKey) {
        CurrentUser u = currentUser();
        if (photoKey == null || photoKey.isBlank()) {
            return jdbc.queryForList("SELECT * FROM ps_ent_manual_review_history WHERE user_id=? ORDER BY created_at DESC LIMIT 200", u.id());
        }
        return jdbc.queryForList("SELECT * FROM ps_ent_manual_review_history WHERE user_id=? AND photo_key=? ORDER BY created_at DESC", u.id(), photoKey);
    }

    public List<Map<String, Object>> listCollections(Long projectId) {
        CurrentUser u = currentUser();
        if (projectId == null) {
            return jdbc.queryForList("SELECT * FROM ps_ent_collection_item WHERE user_id=? ORDER BY sort_no ASC, created_at DESC", u.id());
        }
        return jdbc.queryForList("SELECT * FROM ps_ent_collection_item WHERE user_id=? AND project_id=? ORDER BY sort_no ASC, created_at DESC", u.id(), projectId);
    }

    public Map<String, Object> saveCollection(Map<String, Object> body) {
        CurrentUser u = currentUser();
        String photoKey = str(body, "photoKey", str(body, "fileName", UUID.randomUUID().toString()));
        List<Map<String, Object>> exists = jdbc.queryForList("SELECT id FROM ps_ent_collection_item WHERE user_id=? AND photo_key=?", u.id(), photoKey);
        if (!exists.isEmpty()) {
            jdbc.update("""
                    UPDATE ps_ent_collection_item SET project_id=?, file_name=?, preview_url=?, original_score=?,
                    final_score=?, final_status=?, usage_type=?, client_note=?, sort_no=?, raw_json=?
                    WHERE user_id=? AND photo_key=?
                    """,
                    longVal(body, "projectId"), str(body, "fileName", photoKey), str(body, "previewUrl"),
                    dec(body, "originalScore", 0), dec(body, "finalScore", 0), str(body, "finalStatus", "推荐"),
                    str(body, "usageType", "客户交付"), str(body, "clientNote"), intVal(body, "sortNo", 0),
                    json(body), u.id(), photoKey);
        } else {
            insert("""
                    INSERT INTO ps_ent_collection_item(user_id, project_id, photo_key, file_name, preview_url,
                    original_score, final_score, final_status, usage_type, client_note, sort_no, raw_json)
                    VALUES(?,?,?,?,?,?,?,?,?,?,?,?)
                    """, ps -> {
                ps.setLong(1, u.id());
                ps.setObject(2, longVal(body, "projectId"));
                ps.setString(3, photoKey);
                ps.setString(4, str(body, "fileName", photoKey));
                ps.setString(5, str(body, "previewUrl"));
                ps.setBigDecimal(6, dec(body, "originalScore", 0));
                ps.setBigDecimal(7, dec(body, "finalScore", 0));
                ps.setString(8, str(body, "finalStatus", "推荐"));
                ps.setString(9, str(body, "usageType", "客户交付"));
                ps.setString(10, str(body, "clientNote"));
                ps.setInt(11, intVal(body, "sortNo", 0));
                ps.setString(12, json(body));
            });
        }
        recordUsage(Map.of("eventType", "collection_add", "eventCount", 1, "projectId", longVal(body, "projectId")));
        return Map.of("photoKey", photoKey);
    }

    public void deleteCollection(String photoKey) {
        CurrentUser u = currentUser();
        jdbc.update("DELETE FROM ps_ent_collection_item WHERE user_id=? AND photo_key=?", u.id(), photoKey);
        recordUsage(Map.of("eventType", "collection_delete", "eventCount", 1));
    }

    public void clearCollection(Long projectId) {
        CurrentUser u = currentUser();
        if (projectId == null) jdbc.update("DELETE FROM ps_ent_collection_item WHERE user_id=?", u.id());
        else jdbc.update("DELETE FROM ps_ent_collection_item WHERE user_id=? AND project_id=?", u.id(), projectId);
        recordUsage(Map.of("eventType", "collection_clear", "eventCount", 1, "projectId", projectId));
    }

    public String exportCollectionCsv(Long projectId) {
        List<Map<String, Object>> items = listCollections(projectId);
        StringBuilder sb = new StringBuilder("序号,文件名,最终分数,最终状态,用途,客户备注\n");
        int i = 1;
        for (Map<String, Object> r : items) {
            sb.append(csv(i++)).append(',').append(csv(r.get("file_name"))).append(',').append(csv(r.get("final_score")))
                    .append(',').append(csv(r.get("final_status"))).append(',').append(csv(r.get("usage_type")))
                    .append(',').append(csv(r.get("client_note"))).append('\n');
        }
        recordExport(projectId, null, "COLLECTION_CSV", "精选集清单.csv", Map.of("count", items.size()));
        return sb.toString();
    }

    public String exportCollectionHtml(Long projectId) {
        List<Map<String, Object>> items = listCollections(projectId);
        String html = htmlPage("客户精选查看页", "<h1>客户精选查看页</h1>" + galleryHtml(items));
        recordExport(projectId, null, "COLLECTION_HTML", "客户精选查看页.html", Map.of("count", items.size()));
        return html;
    }

    public byte[] exportCollectionZip(Long projectId) {
        List<Map<String, Object>> items = listCollections(projectId);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bos, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry("精选集清单.csv"));
            zip.write(("\uFEFF" + exportCollectionCsv(projectId)).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("客户精选查看页.html"));
            zip.write(exportCollectionHtml(projectId).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("README.txt"));
            zip.write(("该 ZIP 包包含精选清单和客户查看页。若需要原图打包，请在前端将原始图片文件加入 ZIP。照片数量：" + items.size()).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        } catch (Exception e) {
            throw new RuntimeException("ZIP 生成失败：" + e.getMessage(), e);
        }
        recordExport(projectId, null, "COLLECTION_ZIP", "精选集交付包.zip", Map.of("count", items.size()));
        return bos.toByteArray();
    }

    public Map<String, Object> saveExif(Map<String, Object> body) {
        CurrentUser u = currentUser();
        Long id = insert("""
                INSERT INTO ps_ent_exif_record(user_id, project_id, photo_key, file_name, metadata_status, source_type,
                width, height, mega_pixels, camera_model, lens_model, iso, shutter_speed, aperture, focal_length,
                color_space, diagnosis, raw_json)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, ps -> {
            ps.setLong(1, u.id());
            ps.setObject(2, longVal(body, "projectId"));
            ps.setString(3, str(body, "photoKey"));
            ps.setString(4, str(body, "fileName"));
            ps.setString(5, str(body, "metadataStatus"));
            ps.setString(6, str(body, "sourceType"));
            ps.setObject(7, intVal(body, "width", 0));
            ps.setObject(8, intVal(body, "height", 0));
            ps.setBigDecimal(9, dec(body, "megaPixels", 0));
            ps.setString(10, str(body, "cameraModel"));
            ps.setString(11, str(body, "lensModel"));
            ps.setString(12, str(body, "iso"));
            ps.setString(13, str(body, "shutterSpeed"));
            ps.setString(14, str(body, "aperture"));
            ps.setString(15, str(body, "focalLength"));
            ps.setString(16, str(body, "colorSpace"));
            ps.setString(17, str(body, "diagnosis"));
            ps.setString(18, json(body));
        });
        recordUsage(Map.of("eventType", "exif_analyzed", "eventCount", 1, "projectId", longVal(body, "projectId")));
        return Map.of("id", id);
    }

    public List<Map<String, Object>> listExif(Long projectId) {
        CurrentUser u = currentUser();
        if (projectId == null) return jdbc.queryForList("SELECT * FROM ps_ent_exif_record WHERE user_id=? ORDER BY created_at DESC", u.id());
        return jdbc.queryForList("SELECT * FROM ps_ent_exif_record WHERE user_id=? AND project_id=? ORDER BY created_at DESC", u.id(), projectId);
    }

    public void recordUsage(Map<String, Object> body) {
        CurrentUser u = currentUser();
        jdbc.update("""
                INSERT INTO ps_ent_usage_event(user_id, event_type, event_count, project_id, file_name, meta_json)
                VALUES(?,?,?,?,?,?)
                """, u.id(), str(body, "eventType", "unknown"), intVal(body, "eventCount", 1),
                longVal(body, "projectId"), str(body, "fileName"), json(body.getOrDefault("metaJson", body)));
    }

    public Map<String, Object> usageSummary() {
        CurrentUser u = currentUser();
        Map<String, Object> s = new LinkedHashMap<>();
        String today = LocalDate.now().toString();

        s.put("todayScoreCount", sumEvent(u.id(), "photo_scored", today));
        s.put("todayUploadCount", sumEventLike(u.id(), "upload", today));
        s.put("todayReviewCount", sumEvent(u.id(), "manual_review", today));
        s.put("todayCollectionCount", sumEvent(u.id(), "collection_add", today));
        s.put("todayExifCount", sumEvent(u.id(), "exif_analyzed", today));
        s.put("totalScoreCount", sumEventAll(u.id(), "photo_scored"));
        s.put("totalUploadCount", sumEventAllLike(u.id(), "upload"));
        s.put("totalReviewCount", count("ps_ent_manual_review", u.id()));
        s.put("totalCollectionCount", count("ps_ent_collection_item", u.id()));
        s.put("projectCount", count("ps_ent_project", u.id()));
        s.put("taskCount", count("ps_ent_background_task", u.id()));
        s.put("exportCount", count("ps_ent_export_log", u.id()));
        s.put("quotaText", "无限制");
        s.put("lastActiveAt", lastActiveAt(u.id()));
        return s;
    }

    private int sumEvent(Long userId, String eventType, String date) {
        Integer n = jdbc.queryForObject("SELECT COALESCE(SUM(event_count),0) FROM ps_ent_usage_event WHERE user_id=? AND event_type=? AND DATE(created_at)=?", Integer.class, userId, eventType, date);
        return n == null ? 0 : n;
    }

    private int sumEventLike(Long userId, String eventType, String date) {
        Integer n = jdbc.queryForObject("SELECT COALESCE(SUM(event_count),0) FROM ps_ent_usage_event WHERE user_id=? AND event_type LIKE ? AND DATE(created_at)=?", Integer.class, userId, "%" + eventType + "%", date);
        return n == null ? 0 : n;
    }

    private int sumEventAll(Long userId, String eventType) {
        Integer n = jdbc.queryForObject("SELECT COALESCE(SUM(event_count),0) FROM ps_ent_usage_event WHERE user_id=? AND event_type=?", Integer.class, userId, eventType);
        return n == null ? 0 : n;
    }

    private int sumEventAllLike(Long userId, String eventType) {
        Integer n = jdbc.queryForObject("SELECT COALESCE(SUM(event_count),0) FROM ps_ent_usage_event WHERE user_id=? AND event_type LIKE ?", Integer.class, userId, "%" + eventType + "%");
        return n == null ? 0 : n;
    }

    private int count(String table, Long userId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE user_id=?", Integer.class, userId);
        return n == null ? 0 : n;
    }

    private String lastActiveAt(Long userId) {
        String v = jdbc.queryForObject("SELECT DATE_FORMAT(MAX(created_at),'%Y-%m-%d %H:%i:%s') FROM ps_ent_usage_event WHERE user_id=?", String.class, userId);
        return v == null ? "" : v;
    }

    public Map<String, Object> adminGlobalStatistics() {
        CurrentUser u = currentUser();
        if (!u.admin()) throw new IllegalStateException("只有管理员可以查看全局统计");
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("usersWithUsage", jdbc.queryForObject("SELECT COUNT(DISTINCT user_id) FROM ps_ent_usage_event", Integer.class));
        s.put("projectCount", jdbc.queryForObject("SELECT COUNT(*) FROM ps_ent_project", Integer.class));
        s.put("scoreVersionCount", jdbc.queryForObject("SELECT COUNT(*) FROM ps_ent_score_version", Integer.class));
        s.put("deliveryCount", jdbc.queryForObject("SELECT COUNT(*) FROM ps_ent_delivery_package", Integer.class));
        s.put("exportCount", jdbc.queryForObject("SELECT COUNT(*) FROM ps_ent_export_log", Integer.class));
        return s;
    }

    public Map<String, Object> createDeliveryPackage(Map<String, Object> body) {
        CurrentUser u = currentUser();
        Long packageId = insert("""
                INSERT INTO ps_ent_delivery_package(user_id, project_id, package_name, client_name, version,
                export_mode, client_note, status, raw_json)
                VALUES(?,?,?,?,?,?,?,?,?)
                """, ps -> {
            ps.setLong(1, u.id());
            ps.setObject(2, longVal(body, "projectId"));
            ps.setString(3, str(body, "packageName", "PhotoScore客户交付包"));
            ps.setString(4, str(body, "clientName"));
            ps.setString(5, str(body, "version", "v1"));
            ps.setString(6, str(body, "exportMode", "全部"));
            ps.setString(7, str(body, "clientNote"));
            ps.setString(8, str(body, "status", "草稿"));
            ps.setString(9, json(body));
        });

        Object rawItems = body.get("items");
        if (rawItems instanceof List<?> list) {
            int sort = 1;
            for (Object obj : list) {
                if (obj instanceof Map<?, ?> raw) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    raw.forEach((k, v) -> m.put(String.valueOf(k), v));
                    insertDeliveryItem(packageId, longVal(body, "projectId"), sort++, m);
                }
            }
        }
        recordUsage(Map.of("eventType", "delivery_package_created", "eventCount", 1, "projectId", longVal(body, "projectId")));
        return deliveryDetail(packageId);
    }

    private void insertDeliveryItem(Long packageId, Long projectId, int sort, Map<String, Object> item) {
        CurrentUser u = currentUser();
        insert("""
                INSERT INTO ps_ent_delivery_item(user_id, package_id, project_id, photo_key, file_name, preview_url,
                score, final_status, usage_type, action, sort_no, retouch_required, raw_json)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, ps -> {
            ps.setLong(1, u.id());
            ps.setLong(2, packageId);
            ps.setObject(3, projectId);
            ps.setString(4, str(item, "photoKey", str(item, "fileName", UUID.randomUUID().toString())));
            ps.setString(5, str(item, "fileName"));
            ps.setString(6, str(item, "previewUrl"));
            ps.setBigDecimal(7, dec(item, "score", 0));
            ps.setString(8, str(item, "finalStatus", "推荐"));
            ps.setString(9, str(item, "usageType", "客户交付"));
            ps.setString(10, str(item, "action", ""));
            ps.setInt(11, intVal(item, "sortNo", sort));
            ps.setInt(12, str(item, "finalStatus", "").contains("待修") || str(item, "action", "").contains("修") ? 1 : 0);
            ps.setString(13, json(item));
        });
    }

    public List<Map<String, Object>> listDeliveryPackages(Long projectId) {
        CurrentUser u = currentUser();
        if (projectId == null) return jdbc.queryForList("SELECT * FROM ps_ent_delivery_package WHERE user_id=? ORDER BY updated_at DESC", u.id());
        return jdbc.queryForList("SELECT * FROM ps_ent_delivery_package WHERE user_id=? AND project_id=? ORDER BY updated_at DESC", u.id(), projectId);
    }

    public Map<String, Object> deliveryDetail(Long id) {
        CurrentUser u = currentUser();
        List<Map<String, Object>> p = jdbc.queryForList("SELECT * FROM ps_ent_delivery_package WHERE user_id=? AND id=?", u.id(), id);
        if (p.isEmpty()) return Map.of("id", id, "items", List.of());
        Map<String, Object> result = new LinkedHashMap<>(p.get(0));
        result.put("items", jdbc.queryForList("SELECT * FROM ps_ent_delivery_item WHERE user_id=? AND package_id=? ORDER BY sort_no ASC, id ASC", u.id(), id));
        return result;
    }

    public String exportDeliveryHtml(Long id, String type) {
        Map<String, Object> d = deliveryDetail(id);
        List<Map<String, Object>> items = listFrom(d.get("items"));
        String title = "review".equals(type) ? "批次复盘报告" : "客户交付页";
        StringBuilder body = new StringBuilder();
        body.append("<h1>").append(escape(title)).append("</h1>");
        body.append("<p><b>客户：</b>").append(escape(d.get("client_name"))).append("　<b>版本：</b>").append(escape(d.get("version"))).append("</p>");
        body.append("<p>").append(escape(d.get("client_note"))).append("</p>");
        body.append(galleryHtml(items));
        recordExport(asLong(d.get("project_id")), id, "review".equals(type) ? "DELIVERY_REVIEW_HTML" : "DELIVERY_CLIENT_HTML", title + ".html", Map.of("count", items.size()));
        return htmlPage(title, body.toString());
    }

    public String exportDeliveryCsv(Long id, String type) {
        Map<String, Object> d = deliveryDetail(id);
        List<Map<String, Object>> items = listFrom(d.get("items"));
        StringBuilder sb = new StringBuilder("序号,文件名,分数,最终状态,用途,处理动作\n");
        int i = 1;
        for (Map<String, Object> r : items) {
            if ("retouch".equals(type) && !String.valueOf(r.get("retouch_required")).equals("1")) continue;
            sb.append(csv(i++)).append(',').append(csv(r.get("file_name"))).append(',').append(csv(r.get("score")))
                    .append(',').append(csv(r.get("final_status"))).append(',').append(csv(r.get("usage_type")))
                    .append(',').append(csv(r.get("action"))).append('\n');
        }
        recordExport(asLong(d.get("project_id")), id, "retouch".equals(type) ? "RETOUCH_TASK_CSV" : "DELIVERY_CSV", "retouch".equals(type) ? "修图任务单.csv" : "交付清单.csv", Map.of("packageId", id));
        return sb.toString();
    }

    public byte[] exportDeliveryZip(Long id) {
        Map<String, Object> d = deliveryDetail(id);
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(bos, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry("交付清单.csv"));
            zip.write(("\uFEFF" + exportDeliveryCsv(id, "delivery")).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("修图任务单.csv"));
            zip.write(("\uFEFF" + exportDeliveryCsv(id, "retouch")).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("客户交付页.html"));
            zip.write(exportDeliveryHtml(id, "client").getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("批次复盘报告.html"));
            zip.write(exportDeliveryHtml(id, "review").getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.finish();
            recordExport(asLong(d.get("project_id")), id, "DELIVERY_ZIP", "精选交付包.zip", Map.of("packageId", id));
            return bos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("交付 ZIP 生成失败：" + e.getMessage(), e);
        }
    }

    public List<Map<String, Object>> exportLogs(Long projectId) {
        CurrentUser u = currentUser();
        if (projectId == null) return jdbc.queryForList("SELECT * FROM ps_ent_export_log WHERE user_id=? ORDER BY created_at DESC LIMIT 200", u.id());
        return jdbc.queryForList("SELECT * FROM ps_ent_export_log WHERE user_id=? AND project_id=? ORDER BY created_at DESC LIMIT 200", u.id(), projectId);
    }

    private void recordExport(Long projectId, Long packageId, String type, String fileName, Object meta) {
        CurrentUser u = currentUser();
        jdbc.update("""
                INSERT INTO ps_ent_export_log(user_id, project_id, package_id, export_type, file_name, meta_json)
                VALUES(?,?,?,?,?,?)
                """, u.id(), projectId, packageId, type, fileName, json(meta));
        recordUsage(Map.of("eventType", "export_" + type.toLowerCase(), "eventCount", 1, "projectId", projectId));
    }

    public Map<String, Object> createTask(Map<String, Object> body) {
        CurrentUser u = currentUser();
        Long id = insert("""
                INSERT INTO ps_ent_background_task(user_id, parent_task_id, task_type, external_task_id, status,
                progress, total_count, processed_count, failed_count, message, meta_json)
                VALUES(?,?,?,?,?,?,?,?,?,?,?)
                """, ps -> {
            ps.setLong(1, u.id());
            ps.setObject(2, longVal(body, "parentTaskId"));
            ps.setString(3, str(body, "taskType", "PHOTO_SCORE"));
            ps.setString(4, str(body, "externalTaskId"));
            ps.setString(5, str(body, "status", "PENDING"));
            ps.setInt(6, intVal(body, "progress", 0));
            ps.setInt(7, intVal(body, "totalCount", 0));
            ps.setInt(8, intVal(body, "processedCount", 0));
            ps.setInt(9, intVal(body, "failedCount", 0));
            ps.setString(10, str(body, "message"));
            ps.setString(11, json(body.getOrDefault("metaJson", body)));
        });
        return taskById(id);
    }

    public Map<String, Object> updateTaskProgress(Long id, Map<String, Object> body) {
        CurrentUser u = currentUser();
        jdbc.update("""
                UPDATE ps_ent_background_task SET status=?, progress=?, total_count=?, processed_count=?,
                failed_count=?, message=?, meta_json=? WHERE id=? AND user_id=?
                """, str(body, "status", "RUNNING"), intVal(body, "progress", 0), intVal(body, "totalCount", 0),
                intVal(body, "processedCount", 0), intVal(body, "failedCount", 0), str(body, "message"),
                json(body.getOrDefault("metaJson", body)), id, u.id());
        return taskById(id);
    }

    public Map<String, Object> updateTaskProgressByExternal(Map<String, Object> body) {
        CurrentUser u = currentUser();
        String external = str(body, "externalTaskId");
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id FROM ps_ent_background_task WHERE user_id=? AND external_task_id=? ORDER BY id DESC LIMIT 1", u.id(), external);
        if (rows.isEmpty()) return createTask(body);
        Long id = ((Number) rows.get(0).get("id")).longValue();
        return updateTaskProgress(id, body);
    }

    private Map<String, Object> taskById(Long id) {
        CurrentUser u = currentUser();
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM ps_ent_background_task WHERE user_id=? AND id=?", u.id(), id);
        return rows.isEmpty() ? Map.of("id", id) : rows.get(0);
    }

    public List<Map<String, Object>> listTasks() {
        CurrentUser u = currentUser();
        return jdbc.queryForList("SELECT * FROM ps_ent_background_task WHERE user_id=? ORDER BY updated_at DESC LIMIT 100", u.id());
    }

    public Map<String, Object> retryTask(Long id) {
        Map<String, Object> old = taskById(id);
        if (old.isEmpty()) throw new IllegalStateException("任务不存在");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("parentTaskId", id);
        body.put("taskType", old.get("task_type"));
        body.put("externalTaskId", "retry-" + UUID.randomUUID());
        body.put("status", "PENDING");
        body.put("message", "由失败任务重试创建");
        body.put("metaJson", old);
        return createTask(body);
    }

    public List<Map<String, Object>> restoreTasks() {
        CurrentUser u = currentUser();
        return jdbc.queryForList("""
                SELECT * FROM ps_ent_background_task
                WHERE user_id=? AND status IN ('PENDING','RUNNING','FAILED')
                ORDER BY updated_at DESC LIMIT 50
                """, u.id());
    }

    public Map<String, Object> saveScoreVersion(Map<String, Object> body) {
        CurrentUser u = currentUser();
        String photoKey = str(body, "photoKey", str(body, "fileName", UUID.randomUUID().toString()));
        Integer next = jdbc.queryForObject("SELECT COALESCE(MAX(version_no),0)+1 FROM ps_ent_score_version WHERE user_id=? AND photo_key=?", Integer.class, u.id(), photoKey);
        Long id = insert("""
                INSERT INTO ps_ent_score_version(user_id, project_id, photo_key, file_name, version_no, source,
                final_score, ai_status, ai_comment, suggestion, model_cost_ms, raw_json)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?)
                """, ps -> {
            ps.setLong(1, u.id());
            ps.setObject(2, longVal(body, "projectId"));
            ps.setString(3, photoKey);
            ps.setString(4, str(body, "fileName", photoKey));
            ps.setInt(5, next == null ? 1 : next);
            ps.setString(6, str(body, "source", "PHOTO_SCORE"));
            ps.setBigDecimal(7, dec(body, "finalScore", 0));
            ps.setString(8, str(body, "aiStatus"));
            ps.setString(9, str(body, "aiComment"));
            ps.setString(10, str(body, "suggestion"));
            ps.setInt(11, intVal(body, "modelCostMs", 0));
            ps.setString(12, json(body));
        });
        return Map.of("id", id, "versionNo", next == null ? 1 : next);
    }

    public Map<String, Object> saveScoreVersionBatch(Map<String, Object> body) {
        List<?> items = (List<?>) body.getOrDefault("items", List.of());
        int count = 0;
        for (Object item : items) {
            if (item instanceof Map<?, ?> raw) {
                Map<String, Object> m = new LinkedHashMap<>();
                raw.forEach((k, v) -> m.put(String.valueOf(k), v));
                if (body.containsKey("projectId")) m.put("projectId", body.get("projectId"));
                if (body.containsKey("source")) m.put("source", body.get("source"));
                saveScoreVersion(m);
                count++;
            }
        }
        return Map.of("count", count);
    }

    public List<Map<String, Object>> listScoreVersions(String photoKey) {
        CurrentUser u = currentUser();
        if (photoKey == null || photoKey.isBlank()) {
            return jdbc.queryForList("SELECT * FROM ps_ent_score_version WHERE user_id=? ORDER BY created_at DESC LIMIT 200", u.id());
        }
        return jdbc.queryForList("SELECT * FROM ps_ent_score_version WHERE user_id=? AND photo_key=? ORDER BY version_no DESC", u.id(), photoKey);
    }

    public Map<String, Object> regradeRequest(Long versionId) {
        Map<String, Object> old = jdbc.queryForList("SELECT * FROM ps_ent_score_version WHERE id=? AND user_id=?", versionId, currentUser().id()).stream().findFirst().orElseThrow();
        return createTask(Map.of("taskType", "RE_SCORE", "status", "PENDING", "message", "用户请求重新评分", "metaJson", old));
    }

    public Map<String, Object> saveQcRule(Map<String, Object> body) {
        CurrentUser u = currentUser();
        jdbc.update("""
                INSERT INTO ps_ent_qc_rule(user_id, min_score, min_sharpness, min_resolution_mp, max_noise_risk, print_min_mp, platform_min_mp, raw_json)
                VALUES(?,?,?,?,?,?,?,?)
                ON DUPLICATE KEY UPDATE min_score=VALUES(min_score), min_sharpness=VALUES(min_sharpness),
                min_resolution_mp=VALUES(min_resolution_mp), max_noise_risk=VALUES(max_noise_risk),
                print_min_mp=VALUES(print_min_mp), platform_min_mp=VALUES(platform_min_mp), raw_json=VALUES(raw_json)
                """, u.id(), dec(body, "minScore", 60), dec(body, "minSharpness", 40), dec(body, "minResolutionMp", 1),
                dec(body, "maxNoiseRisk", 70), dec(body, "printMinMp", 6), dec(body, "platformMinMp", 1.5), json(body));
        return getQcRule();
    }

    public Map<String, Object> getQcRule() {
        CurrentUser u = currentUser();
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM ps_ent_qc_rule WHERE user_id=?", u.id());
        return rows.isEmpty() ? Map.of("minScore", 60, "minSharpness", 40, "minResolutionMp", 1, "maxNoiseRisk", 70, "printMinMp", 6, "platformMinMp", 1.5) : rows.get(0);
    }

    public Map<String, Object> saveQcRecord(Map<String, Object> body) {
        CurrentUser u = currentUser();
        Long id = insert("""
                INSERT INTO ps_ent_qc_record(user_id, project_id, report_type, qc_level, issue_tags, html_snapshot, raw_json)
                VALUES(?,?,?,?,?,?,?)
                """, ps -> {
            ps.setLong(1, u.id());
            ps.setObject(2, longVal(body, "projectId"));
            ps.setString(3, str(body, "reportType", "QC"));
            ps.setString(4, str(body, "qcLevel"));
            ps.setString(5, str(body, "issueTags"));
            ps.setString(6, str(body, "htmlSnapshot"));
            ps.setString(7, json(body));
        });
        recordUsage(Map.of("eventType", "qc_record", "eventCount", 1, "projectId", longVal(body, "projectId")));
        return Map.of("id", id);
    }

    public String exportQcRecordHtml(Long id) {
        CurrentUser u = currentUser();
        Map<String, Object> r = jdbc.queryForList("SELECT * FROM ps_ent_qc_record WHERE user_id=? AND id=?", u.id(), id).stream().findFirst().orElse(Map.of());
        String snapshot = String.valueOf(r.getOrDefault("html_snapshot", ""));
        return htmlPage("质检报告", "<h1>PhotoScore Pro 质检报告</h1>" + snapshot);
    }

    public Map<String, Object> analyticsHistory(String range) {
        CurrentUser u = currentUser();
        Map<String, Object> m = new LinkedHashMap<>();
        String dateSql = "all".equalsIgnoreCase(range) ? "" : " AND created_at >= DATE_SUB(NOW(), INTERVAL " + ("7".equals(range) ? "7" : "30") + " DAY)";
        m.put("projectCount", count("ps_ent_project", u.id()));
        m.put("scoreVersionCount", queryInt("SELECT COUNT(*) FROM ps_ent_score_version WHERE user_id=?" + dateSql, u.id()));
        m.put("reviewCount", queryInt("SELECT COUNT(*) FROM ps_ent_manual_review WHERE user_id=?" + dateSql, u.id()));
        m.put("collectionCount", count("ps_ent_collection_item", u.id()));
        m.put("exportCount", queryInt("SELECT COUNT(*) FROM ps_ent_export_log WHERE user_id=?" + dateSql, u.id()));
        m.put("failedTaskCount", queryInt("SELECT COUNT(*) FROM ps_ent_background_task WHERE user_id=? AND status='FAILED'" + dateSql, u.id()));
        m.put("summary", "历史复盘已基于数据库生成：优先使用人工复核后的最终结果、精选集和导出日志统计。");
        return m;
    }

    private int queryInt(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    private String csv(Object v) {
        return "\"" + String.valueOf(v == null ? "" : v).replace("\"", "\"\"") + "\"";
    }

    private String escape(Object v) {
        return String.valueOf(v == null ? "" : v)
                .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#039;");
    }

    private String galleryHtml(List<Map<String, Object>> items) {
        StringBuilder sb = new StringBuilder("""
                <style>
                body{font-family:Arial,'Microsoft YaHei',sans-serif;padding:28px;background:#f7f4ec;color:#161616}
                .grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(220px,1fr));gap:16px}
                .card{background:white;border:1px solid #ddd;border-radius:16px;overflow:hidden;box-shadow:0 10px 28px rgba(0,0,0,.08)}
                img{width:100%;height:160px;object-fit:cover;background:#111827}
                .body{padding:12px;font-size:13px;line-height:1.7}.score{font-size:20px;font-weight:800;color:#f97316}
                </style><div class="grid">
                """);
        for (Map<String, Object> r : items) {
            sb.append("<div class='card'>");
            if (r.get("preview_url") != null) sb.append("<img src='").append(escape(r.get("preview_url"))).append("'>");
            sb.append("<div class='body'><b>").append(escape(r.get("file_name"))).append("</b><br>")
                    .append("<span class='score'>").append(escape(r.getOrDefault("final_score", r.get("score")))).append("</span><br>")
                    .append("状态：").append(escape(r.getOrDefault("final_status", ""))).append("<br>")
                    .append("用途：").append(escape(r.getOrDefault("usage_type", ""))).append("</div></div>");
        }
        sb.append("</div>");
        return sb.toString();
    }

    private String htmlPage(String title, String body) {
        return "<!doctype html><html lang='zh-CN'><head><meta charset='utf-8'><title>" + escape(title) + "</title></head><body>" + body + "</body></html>";
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listFrom(Object v) {
        if (v instanceof List<?> l) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object o : l) if (o instanceof Map<?, ?> m) {
                Map<String, Object> item = new LinkedHashMap<>();
                m.forEach((k, val) -> item.put(String.valueOf(k), val));
                out.add(item);
            }
            return out;
        }
        return List.of();
    }

    private Long asLong(Object v) {
        if (v instanceof Number n) return n.longValue();
        if (v == null) return null;
        try { return Long.parseLong(String.valueOf(v)); } catch (Exception e) { return null; }
    }
}
