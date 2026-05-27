
package com.example.photoscore.controller;

import com.example.photoscore.pojo.ApiResponse;
import com.example.photoscore.service.ProPlusService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/workspace/proplus")
@RequiredArgsConstructor
public class ProPlusController {

    private final ProPlusService proPlusService;

    @GetMapping("/qc/rules")
    public ApiResponse<Map<String, Object>> getQcRules() {
        return ApiResponse.success(proPlusService.getQcRules());
    }

    @PutMapping("/qc/rules")
    public ApiResponse<Map<String, Object>> saveQcRules(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(proPlusService.saveQcRules(body));
    }

    @PostMapping("/qc/record")
    public ApiResponse<Map<String, Object>> saveQcRecord(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(proPlusService.saveQcRecord(body));
    }

    @GetMapping("/qc/records")
    public ApiResponse<Object> listQcRecords(@RequestParam(required = false) Long projectId) {
        return ApiResponse.success(proPlusService.listQcRecords(projectId));
    }

    @PostMapping("/delivery/package")
    public ApiResponse<Map<String, Object>> saveDeliveryPackage(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(proPlusService.saveDeliveryPackage(body));
    }

    @GetMapping("/delivery/packages")
    public ApiResponse<Object> listDeliveryPackages(@RequestParam(required = false) Long projectId) {
        return ApiResponse.success(proPlusService.listDeliveryPackages(projectId));
    }

    @GetMapping("/delivery/package/{id}/export.csv")
    public ResponseEntity<byte[]> exportDeliveryPackageCsv(@PathVariable Long id) {
        String csv = proPlusService.exportDeliveryPackageCsv(id);
        String fileName = "PhotoScore-Pro-Delivery-" + id + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(("\uFEFF" + csv).getBytes(StandardCharsets.UTF_8));
    }

    @PostMapping("/project-version")
    public ApiResponse<Map<String, Object>> saveProjectVersion(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(proPlusService.saveProjectVersion(body));
    }

    @GetMapping("/project-version")
    public ApiResponse<Object> listProjectVersions(@RequestParam(required = false) Long projectId) {
        return ApiResponse.success(proPlusService.listProjectVersions(projectId));
    }

    @GetMapping("/analytics/advanced")
    public ApiResponse<Map<String, Object>> advancedAnalytics(@RequestParam(required = false) Integer days) {
        return ApiResponse.success(proPlusService.advancedAnalytics(days == null ? 30 : days));
    }

    @PostMapping("/usage/event")
    public ApiResponse<Map<String, Object>> recordUsage(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(proPlusService.recordUsage(body));
    }

    @GetMapping("/usage/summary")
    public ApiResponse<Map<String, Object>> usageSummary() {
        return ApiResponse.success(proPlusService.usageSummary());
    }
}
