
package com.example.photoscore.controller;

import com.example.photoscore.service.EnterpriseWorkspaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/workspace/enterprise")
@RequiredArgsConstructor
public class EnterpriseWorkspaceController {

    private final EnterpriseWorkspaceService service;

    private Map<String, Object> ok(Object data) {
        return Map.of("code", 200, "message", "success", "data", data == null ? Map.of() : data);
    }

    @GetMapping("/projects")
    public Map<String, Object> projects() {
        return ok(service.listProjects());
    }

    @PostMapping("/projects")
    public Map<String, Object> saveProject(@RequestBody Map<String, Object> body) {
        return ok(service.saveProject(body));
    }

    @PutMapping("/projects/{id}")
    public Map<String, Object> updateProject(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        body.put("id", id);
        return ok(service.saveProject(body));
    }

    @DeleteMapping("/projects/{id}")
    public Map<String, Object> deleteProject(@PathVariable Long id) {
        service.deleteProject(id);
        return ok(true);
    }

    @PostMapping("/projects/{id}/version")
    public Map<String, Object> saveProjectVersion(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ok(service.saveProjectVersion(id, body));
    }

    @GetMapping("/manual-reviews")
    public Map<String, Object> manualReviews(@RequestParam(required = false) Long projectId) {
        return ok(service.listManualReviews(projectId));
    }

    @GetMapping("/manual-reviews/one")
    public Map<String, Object> manualReviewOne(@RequestParam String photoKey) {
        return ok(service.getManualReview(photoKey));
    }

    @PutMapping("/manual-reviews")
    public Map<String, Object> saveManualReview(@RequestBody Map<String, Object> body) {
        return ok(service.saveManualReview(body));
    }

    @PostMapping("/manual-reviews/batch")
    public Map<String, Object> saveManualReviewBatch(@RequestBody Map<String, Object> body) {
        return ok(service.saveManualReviewBatch(body));
    }

    @DeleteMapping("/manual-reviews")
    public Map<String, Object> deleteManualReview(@RequestParam String photoKey) {
        service.deleteManualReview(photoKey);
        return ok(true);
    }

    @GetMapping("/manual-reviews/history")
    public Map<String, Object> manualReviewHistory(@RequestParam(required = false) String photoKey) {
        return ok(service.listManualReviewHistory(photoKey));
    }

    @GetMapping("/collections")
    public Map<String, Object> collections(@RequestParam(required = false) Long projectId) {
        return ok(service.listCollections(projectId));
    }

    @PostMapping("/collections")
    public Map<String, Object> saveCollection(@RequestBody Map<String, Object> body) {
        return ok(service.saveCollection(body));
    }

    @DeleteMapping("/collections")
    public Map<String, Object> deleteCollection(@RequestParam String photoKey) {
        service.deleteCollection(photoKey);
        return ok(true);
    }

    @DeleteMapping("/collections/clear")
    public Map<String, Object> clearCollection(@RequestParam(required = false) Long projectId) {
        service.clearCollection(projectId);
        return ok(true);
    }

    @GetMapping("/collections/export.csv")
    public ResponseEntity<byte[]> exportCollectionCsv(@RequestParam(required = false) Long projectId) {
        String csv = service.exportCollectionCsv(projectId);
        return download("精选集清单.csv", "text/csv;charset=UTF-8", ("\uFEFF" + csv).getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/collections/export.html")
    public ResponseEntity<byte[]> exportCollectionHtml(@RequestParam(required = false) Long projectId) {
        String html = service.exportCollectionHtml(projectId);
        return download("客户精选查看页.html", "text/html;charset=UTF-8", html.getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/collections/export.zip")
    public ResponseEntity<byte[]> exportCollectionZip(@RequestParam(required = false) Long projectId) {
        byte[] zip = service.exportCollectionZip(projectId);
        return download("精选集交付包.zip", "application/zip", zip);
    }

    @PostMapping("/exif")
    public Map<String, Object> saveExif(@RequestBody Map<String, Object> body) {
        return ok(service.saveExif(body));
    }

    @GetMapping("/exif")
    public Map<String, Object> listExif(@RequestParam(required = false) Long projectId) {
        return ok(service.listExif(projectId));
    }

    @PostMapping("/usage/event")
    public Map<String, Object> usageEvent(@RequestBody Map<String, Object> body) {
        service.recordUsage(body);
        return ok(true);
    }

    @GetMapping("/usage/summary")
    public Map<String, Object> usageSummary() {
        return ok(service.usageSummary());
    }

    @GetMapping("/admin/global-statistics")
    public Map<String, Object> adminGlobalStatistics() {
        return ok(service.adminGlobalStatistics());
    }

    @PostMapping("/deliveries")
    public Map<String, Object> createDelivery(@RequestBody Map<String, Object> body) {
        return ok(service.createDeliveryPackage(body));
    }

    @GetMapping("/deliveries")
    public Map<String, Object> listDeliveries(@RequestParam(required = false) Long projectId) {
        return ok(service.listDeliveryPackages(projectId));
    }

    @GetMapping("/deliveries/{id}")
    public Map<String, Object> deliveryDetail(@PathVariable Long id) {
        return ok(service.deliveryDetail(id));
    }

    @GetMapping("/deliveries/{id}/export-html")
    public ResponseEntity<byte[]> deliveryHtml(@PathVariable Long id) {
        String html = service.exportDeliveryHtml(id, "client");
        return download("客户交付页.html", "text/html;charset=UTF-8", html.getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/deliveries/{id}/export-review-html")
    public ResponseEntity<byte[]> deliveryReviewHtml(@PathVariable Long id) {
        String html = service.exportDeliveryHtml(id, "review");
        return download("批次复盘报告.html", "text/html;charset=UTF-8", html.getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/deliveries/{id}/export-csv")
    public ResponseEntity<byte[]> deliveryCsv(@PathVariable Long id) {
        String csv = service.exportDeliveryCsv(id, "delivery");
        return download("交付清单.csv", "text/csv;charset=UTF-8", ("\uFEFF" + csv).getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/deliveries/{id}/export-retouch")
    public ResponseEntity<byte[]> retouchCsv(@PathVariable Long id) {
        String csv = service.exportDeliveryCsv(id, "retouch");
        return download("修图任务单.csv", "text/csv;charset=UTF-8", ("\uFEFF" + csv).getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/deliveries/{id}/export-zip")
    public ResponseEntity<byte[]> deliveryZip(@PathVariable Long id) {
        return download("精选交付包.zip", "application/zip", service.exportDeliveryZip(id));
    }

    @GetMapping("/export-logs")
    public Map<String, Object> exportLogs(@RequestParam(required = false) Long projectId) {
        return ok(service.exportLogs(projectId));
    }

    @PostMapping("/tasks")
    public Map<String, Object> createTask(@RequestBody Map<String, Object> body) {
        return ok(service.createTask(body));
    }

    @PostMapping("/tasks/{id}/progress")
    public Map<String, Object> updateTask(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ok(service.updateTaskProgress(id, body));
    }

    @PostMapping("/tasks/progress-by-external")
    public Map<String, Object> updateTaskByExternal(@RequestBody Map<String, Object> body) {
        return ok(service.updateTaskProgressByExternal(body));
    }

    @GetMapping("/tasks")
    public Map<String, Object> tasks() {
        return ok(service.listTasks());
    }

    @PostMapping("/tasks/{id}/retry")
    public Map<String, Object> retryTask(@PathVariable Long id) {
        return ok(service.retryTask(id));
    }

    @GetMapping("/tasks/restore")
    public Map<String, Object> restoreTasks() {
        return ok(service.restoreTasks());
    }

    @PostMapping("/score-versions")
    public Map<String, Object> saveScoreVersion(@RequestBody Map<String, Object> body) {
        return ok(service.saveScoreVersion(body));
    }

    @PostMapping("/score-versions/batch")
    public Map<String, Object> saveScoreVersionBatch(@RequestBody Map<String, Object> body) {
        return ok(service.saveScoreVersionBatch(body));
    }

    @GetMapping("/score-versions")
    public Map<String, Object> listScoreVersions(@RequestParam(required = false) String photoKey) {
        return ok(service.listScoreVersions(photoKey));
    }

    @PostMapping("/score-versions/{id}/regrade-request")
    public Map<String, Object> regradeRequest(@PathVariable Long id) {
        return ok(service.regradeRequest(id));
    }

    @PostMapping("/qc/rules")
    public Map<String, Object> saveQcRule(@RequestBody Map<String, Object> body) {
        return ok(service.saveQcRule(body));
    }

    @GetMapping("/qc/rules")
    public Map<String, Object> getQcRule() {
        return ok(service.getQcRule());
    }

    @PostMapping("/qc/records")
    public Map<String, Object> saveQcRecord(@RequestBody Map<String, Object> body) {
        return ok(service.saveQcRecord(body));
    }

    @GetMapping("/qc/records/{id}/export-html")
    public ResponseEntity<byte[]> exportQcRecord(@PathVariable Long id) {
        String html = service.exportQcRecordHtml(id);
        return download("质检报告.html", "text/html;charset=UTF-8", html.getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/analytics/history")
    public Map<String, Object> analyticsHistory(@RequestParam(defaultValue = "30") String range) {
        return ok(service.analyticsHistory(range));
    }

    private ResponseEntity<byte[]> download(String fileName, String contentType, byte[] bytes) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(fileName, StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType(contentType))
                .body(bytes);
    }
}
