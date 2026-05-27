
package com.example.photoscore.service;

import com.example.photoscore.security.UserContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ProPlusService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    private Long userId() {
        Long id = UserContext.getUserId();
        if (id == null) {
            throw new IllegalStateException("请先登录");
        }
        return id;
    }

    private String json(Object o) {
        try { return objectMapper.writeValueAsString(o == null ? Map.of() : o); }
        catch (Exception e) { return "{}"; }
    }

    private Map<String, Object> parseJson(String s) {
        try {
            if (s == null || s.isBlank()) return new LinkedHashMap<>();
            return objectMapper.readValue(s, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private BigDecimal bd(Object v, double fallback) {
        if (v == null || String.valueOf(v).isBlank()) return BigDecimal.valueOf(fallback);
        try { return new BigDecimal(String.valueOf(v)); }
        catch (Exception e) { return BigDecimal.valueOf(fallback); }
    }

    private Long lng(Object v) {
        if (v == null || String.valueOf(v).isBlank()) return null;
        try { return Long.parseLong(String.valueOf(v)); }
        catch (Exception e) { return null; }
    }

    private String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    public Map<String, Object> getQcRules() {
        Long uid = userId();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM ps_proplus_qc_rule WHERE user_id=? LIMIT 1", uid
        );
        if (rows.isEmpty()) {
            return new LinkedHashMap<>(Map.of(
                    "minScore", 70,
                    "minSharpness", 60,
                    "minResolutionMp", 2,
                    "maxNoiseRisk", 45,
                    "printMinMp", 8,
                    "platformMinMp", 1.2
            ));
        }
        Map<String, Object> r = rows.get(0);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("minScore", r.get("min_score"));
        out.put("minSharpness", r.get("min_sharpness"));
        out.put("minResolutionMp", r.get("min_resolution_mp"));
        out.put("maxNoiseRisk", r.get("max_noise_risk"));
        out.put("printMinMp", r.get("print_min_mp"));
        out.put("platformMinMp", r.get("platform_min_mp"));
        out.put("raw", parseJson((String) r.get("raw_json")));
        return out;
    }

    @Transactional
    public Map<String, Object> saveQcRules(Map<String, Object> body) {
        Long uid = userId();
        jdbcTemplate.update("""
                INSERT INTO ps_proplus_qc_rule
                (user_id, min_score, min_sharpness, min_resolution_mp, max_noise_risk, print_min_mp, platform_min_mp, raw_json)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                min_score=VALUES(min_score),
                min_sharpness=VALUES(min_sharpness),
                min_resolution_mp=VALUES(min_resolution_mp),
                max_noise_risk=VALUES(max_noise_risk),
                print_min_mp=VALUES(print_min_mp),
                platform_min_mp=VALUES(platform_min_mp),
                raw_json=VALUES(raw_json),
                updated_at=CURRENT_TIMESTAMP
                """,
                uid,
                bd(body.get("minScore"), 70),
                bd(body.get("minSharpness"), 60),
                bd(body.get("minResolutionMp"), 2),
                bd(body.get("maxNoiseRisk"), 45),
                bd(body.get("printMinMp"), 8),
                bd(body.get("platformMinMp"), 1.2),
                json(body)
        );
        return getQcRules();
    }

    @Transactional
    public Map<String, Object> saveQcRecord(Map<String, Object> body) {
        Long uid = userId();
        String photoKey = Objects.toString(body.getOrDefault("photoKey", body.getOrDefault("fileName", "")), "").trim();
        if (photoKey.isEmpty()) throw new IllegalArgumentException("photoKey 不能为空");
        String fileName = Objects.toString(body.getOrDefault("fileName", photoKey), photoKey);
        Long projectId = lng(body.get("projectId"));

        jdbcTemplate.update("""
                INSERT INTO ps_proplus_qc_record
                (user_id, project_id, photo_key, file_name, score, qc_level, issue_tags, action_advice, report_json)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                project_id=VALUES(project_id),
                file_name=VALUES(file_name),
                score=VALUES(score),
                qc_level=VALUES(qc_level),
                issue_tags=VALUES(issue_tags),
                action_advice=VALUES(action_advice),
                report_json=VALUES(report_json),
                updated_at=CURRENT_TIMESTAMP
                """,
                uid,
                projectId,
                photoKey,
                fileName,
                bd(body.get("score"), 0),
                str(body.get("qcLevel")),
                str(body.get("issueTags")),
                str(body.get("actionAdvice")),
                json(body)
        );
        recordUsage(Map.of("eventType", "quality_check", "projectId", projectId, "meta", body));
        return body;
    }

    public List<Map<String, Object>> listQcRecords(Long projectId) {
        Long uid = userId();
        String sql = "SELECT * FROM ps_proplus_qc_record WHERE user_id=?";
        List<Object> args = new ArrayList<>();
        args.add(uid);
        if (projectId != null) {
            sql += " AND project_id=?";
            args.add(projectId);
        }
        sql += " ORDER BY updated_at DESC";
        return jdbcTemplate.query(sql, (rs, i) -> qcRecordRow(rs), args.toArray());
    }

    private Map<String, Object> qcRecordRow(ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", rs.getLong("id"));
        m.put("projectId", rs.getObject("project_id"));
        m.put("photoKey", rs.getString("photo_key"));
        m.put("fileName", rs.getString("file_name"));
        m.put("score", rs.getBigDecimal("score"));
        m.put("qcLevel", rs.getString("qc_level"));
        m.put("issueTags", rs.getString("issue_tags"));
        m.put("actionAdvice", rs.getString("action_advice"));
        m.put("report", parseJson(rs.getString("report_json")));
        m.put("updatedAt", rs.getTimestamp("updated_at"));
        return m;
    }

    @Transactional
    public Map<String, Object> saveDeliveryPackage(Map<String, Object> body) {
        Long uid = userId();
        Long projectId = lng(body.get("projectId"));
        String name = Objects.toString(body.getOrDefault("packageName", "PhotoScore 客户交付包"));
        List<?> items = body.get("items") instanceof List<?> list ? list : List.of();

        jdbcTemplate.update("""
                INSERT INTO ps_proplus_delivery_package
                (user_id, project_id, package_name, client_name, version_code, export_mode, note, summary_json)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                uid,
                projectId,
                name,
                str(body.get("clientName")),
                str(body.get("version")),
                str(body.get("exportMode")),
                str(body.get("note")),
                json(body)
        );
        Long packageId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        int order = 1;
        for (Object itemObj : items) {
            Map<String, Object> item = itemObj instanceof Map<?,?> mm ? castMap(mm) : Map.of();
            jdbcTemplate.update("""
                    INSERT INTO ps_proplus_delivery_item
                    (package_id, user_id, project_id, photo_key, file_name, score, final_group, qc_level, use_advice, action_advice, sort_order, item_json)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    packageId,
                    uid,
                    projectId,
                    str(item.get("photoKey")),
                    str(item.get("fileName")),
                    bd(item.get("score"), 0),
                    str(item.get("finalGroup")),
                    str(item.get("qcLevel")),
                    str(item.get("useAdvice")),
                    str(item.get("actionAdvice")),
                    order++,
                    json(item)
            );
        }
        recordUsage(Map.of("eventType", "delivery_export", "projectId", projectId, "meta", body));
        return new LinkedHashMap<>(Map.of("packageId", packageId, "itemCount", items.size()));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Map<?,?> m) {
        Map<String, Object> out = new LinkedHashMap<>();
        m.forEach((k, v) -> out.put(String.valueOf(k), v));
        return out;
    }

    public List<Map<String, Object>> listDeliveryPackages(Long projectId) {
        Long uid = userId();
        String sql = "SELECT * FROM ps_proplus_delivery_package WHERE user_id=?";
        List<Object> args = new ArrayList<>();
        args.add(uid);
        if (projectId != null) {
            sql += " AND project_id=?";
            args.add(projectId);
        }
        sql += " ORDER BY updated_at DESC";
        return jdbcTemplate.queryForList(sql, args.toArray());
    }

    public String exportDeliveryPackageCsv(Long packageId) {
        Long uid = userId();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM ps_proplus_delivery_item WHERE package_id=? AND user_id=? ORDER BY sort_order ASC",
                packageId, uid
        );
        StringBuilder sb = new StringBuilder();
        sb.append("序号,文件名,分数,最终分组,质检等级,适合用途,处理动作\n");
        int i = 1;
        for (Map<String, Object> r : rows) {
            sb.append(csv(i++)).append(',')
                    .append(csv(r.get("file_name"))).append(',')
                    .append(csv(r.get("score"))).append(',')
                    .append(csv(r.get("final_group"))).append(',')
                    .append(csv(r.get("qc_level"))).append(',')
                    .append(csv(r.get("use_advice"))).append(',')
                    .append(csv(r.get("action_advice"))).append('\n');
        }
        return sb.toString();
    }

    private String csv(Object v) {
        String s = v == null ? "" : String.valueOf(v);
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    @Transactional
    public Map<String, Object> saveProjectVersion(Map<String, Object> body) {
        Long uid = userId();
        Long projectId = lng(body.get("projectId"));
        jdbcTemplate.update("""
                INSERT INTO ps_proplus_project_version
                (user_id, project_id, project_name, version_code, project_status, cover_url, photo_count, avg_score, selected_count, retouch_count, delivered_count, snapshot_json)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                uid,
                projectId,
                str(body.get("projectName")),
                str(body.getOrDefault("version", "v1")),
                str(body.getOrDefault("status", "进行中")),
                str(body.get("coverUrl")),
                intVal(body.get("photoCount")),
                bd(body.get("avgScore"), 0),
                intVal(body.get("selectedCount")),
                intVal(body.get("retouchCount")),
                intVal(body.get("deliveredCount")),
                json(body)
        );
        recordUsage(Map.of("eventType", "project_version", "projectId", projectId, "meta", body));
        return body;
    }

    private int intVal(Object v) {
        if (v == null) return 0;
        try { return Integer.parseInt(String.valueOf(v)); }
        catch (Exception e) { return 0; }
    }

    public List<Map<String, Object>> listProjectVersions(Long projectId) {
        Long uid = userId();
        String sql = "SELECT * FROM ps_proplus_project_version WHERE user_id=?";
        List<Object> args = new ArrayList<>();
        args.add(uid);
        if (projectId != null) {
            sql += " AND project_id=?";
            args.add(projectId);
        }
        sql += " ORDER BY created_at DESC";
        return jdbcTemplate.queryForList(sql, args.toArray());
    }

    public Map<String, Object> advancedAnalytics(int days) {
        Long uid = userId();
        LocalDate from = LocalDate.now().minusDays(days <= 0 ? 30 : days);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("days", days);
        out.put("from", from.toString());
        out.put("todayUpload", countUsage(uid, "upload", from));
        out.put("todayScore", countUsage(uid, "score", from));
        out.put("qualityCheck", countUsage(uid, "quality_check", from));
        out.put("manualReview", countUsage(uid, "manual_review", from));
        out.put("deliveryExport", countUsage(uid, "delivery_export", from));
        out.put("projectVersion", countUsage(uid, "project_version", from));
        out.put("qcLevelStats", jdbcTemplate.queryForList("""
                SELECT qc_level AS name, COUNT(*) AS count
                FROM ps_proplus_qc_record
                WHERE user_id=? AND updated_at >= ?
                GROUP BY qc_level
                ORDER BY count DESC
                """, uid, from.atStartOfDay()));
        out.put("issueStats", jdbcTemplate.queryForList("""
                SELECT issue_tags
                FROM ps_proplus_qc_record
                WHERE user_id=? AND updated_at >= ?
                """, uid, from.atStartOfDay()));
        out.put("generatedAt", LocalDateTime.now().toString());
        return out;
    }

    private int countUsage(Long uid, String type, LocalDate from) {
        Integer n = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(event_count),0)
                FROM ps_proplus_usage_event
                WHERE user_id=? AND event_type=? AND created_at >= ?
                """, Integer.class, uid, type, from.atStartOfDay());
        return n == null ? 0 : n;
    }

    @Transactional
    public Map<String, Object> recordUsage(Map<String, Object> body) {
        Long uid = userId();
        String type = Objects.toString(body.getOrDefault("eventType", body.getOrDefault("type", "unknown")));
        Long projectId = lng(body.get("projectId"));
        int count = intVal(body.getOrDefault("eventCount", 1));
        jdbcTemplate.update("""
                INSERT INTO ps_proplus_usage_event
                (user_id, event_type, event_count, project_id, meta_json)
                VALUES (?, ?, ?, ?, ?)
                """, uid, type, count <= 0 ? 1 : count, projectId, json(body.getOrDefault("meta", body)));
        return new LinkedHashMap<>(Map.of("eventType", type, "eventCount", count <= 0 ? 1 : count));
    }

    public Map<String, Object> usageSummary() {
        Long uid = userId();
        LocalDate today = LocalDate.now();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("todayUploaded", countUsage(uid, "upload", today));
        out.put("todayScored", countUsage(uid, "score", today));
        out.put("todayQualityCheck", countUsage(uid, "quality_check", today));
        out.put("todayManualReview", countUsage(uid, "manual_review", today));
        out.put("todayExport", countUsage(uid, "delivery_export", today) + countUsage(uid, "export", today));
        out.put("todayAvailableQuota", "无限制");
        out.put("totalProjects", jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ps_proplus_project_version WHERE user_id=?", Integer.class, uid));
        out.put("totalQualityRecords", jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ps_proplus_qc_record WHERE user_id=?", Integer.class, uid));
        out.put("totalDeliveryPackages", jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ps_proplus_delivery_package WHERE user_id=?", Integer.class, uid));
        out.put("lastActiveAt", jdbcTemplate.queryForObject("SELECT MAX(created_at) FROM ps_proplus_usage_event WHERE user_id=?", Object.class, uid));
        return out;
    }
}
