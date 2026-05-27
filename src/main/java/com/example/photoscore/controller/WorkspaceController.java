package com.example.photoscore.controller;


import com.example.photoscore.dto.WorkspaceApiResponse;
import com.example.photoscore.dto.WorkspaceDtos;
import com.example.photoscore.pojo.*;
import com.example.photoscore.service.WorkspaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/workspace")
@RequiredArgsConstructor
public class WorkspaceController {
    private final WorkspaceService workspaceService;

    @GetMapping("/projects")
    public WorkspaceApiResponse<List<ProjectEntity>> projects() {
        return WorkspaceApiResponse.success(workspaceService.listProjects());
    }

    @PostMapping("/projects")
    public WorkspaceApiResponse<ProjectEntity> createProject(@RequestBody WorkspaceDtos.ProjectRequest request) {
        try { return WorkspaceApiResponse.success(workspaceService.createProject(request)); }
        catch (Exception e) { return WorkspaceApiResponse.fail(e.getMessage()); }
    }

    @PutMapping("/projects/{id}")
    public WorkspaceApiResponse<ProjectEntity> updateProject(@PathVariable Long id, @RequestBody WorkspaceDtos.ProjectRequest request) {
        try { return WorkspaceApiResponse.success(workspaceService.updateProject(id, request)); }
        catch (Exception e) { return WorkspaceApiResponse.fail(e.getMessage()); }
    }

    @DeleteMapping("/projects/{id}")
    public WorkspaceApiResponse<Boolean> deleteProject(@PathVariable Long id) {
        try { workspaceService.deleteProject(id); return WorkspaceApiResponse.success(true); }
        catch (Exception e) { return WorkspaceApiResponse.fail(e.getMessage()); }
    }

    @GetMapping("/manual-reviews")
    public WorkspaceApiResponse<List<ManualReviewEntity>> manualReviews(@RequestParam(required = false) Long projectId) {
        return WorkspaceApiResponse.success(workspaceService.listManualReviews(projectId));
    }

    @GetMapping("/manual-reviews/one")
    public WorkspaceApiResponse<ManualReviewEntity> manualReviewOne(@RequestParam String photoKey) {
        return WorkspaceApiResponse.success(workspaceService.oneManualReview(photoKey));
    }

    @PutMapping("/manual-reviews")
    public WorkspaceApiResponse<ManualReviewEntity> upsertManualReview(@RequestBody WorkspaceDtos.ManualReviewRequest request) {
        try { return WorkspaceApiResponse.success(workspaceService.upsertManualReview(request)); }
        catch (Exception e) { return WorkspaceApiResponse.fail(e.getMessage()); }
    }

    @DeleteMapping("/manual-reviews")
    public WorkspaceApiResponse<Boolean> deleteManualReview(@RequestParam String photoKey) {
        try { workspaceService.deleteManualReview(photoKey); return WorkspaceApiResponse.success(true); }
        catch (Exception e) { return WorkspaceApiResponse.fail(e.getMessage()); }
    }

    @GetMapping("/collections")
    public WorkspaceApiResponse<List<CollectionItemEntity>> collections(@RequestParam(required = false) Long projectId) {
        return WorkspaceApiResponse.success(workspaceService.listCollections(projectId));
    }

    @PostMapping("/collections")
    public WorkspaceApiResponse<CollectionItemEntity> addCollection(@RequestBody WorkspaceDtos.CollectionItemRequest request) {
        try { return WorkspaceApiResponse.success(workspaceService.addCollection(request)); }
        catch (Exception e) { return WorkspaceApiResponse.fail(e.getMessage()); }
    }

    @DeleteMapping("/collections")
    public WorkspaceApiResponse<Boolean> deleteCollection(@RequestParam String photoKey) {
        try { workspaceService.deleteCollection(photoKey); return WorkspaceApiResponse.success(true); }
        catch (Exception e) { return WorkspaceApiResponse.fail(e.getMessage()); }
    }

    @GetMapping("/collections/export.csv")
    public ResponseEntity<byte[]> exportCollectionsCsv(@RequestParam(required = false) Long projectId) {
        byte[] data = workspaceService.exportCollectionsCsv(projectId);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename("photoscore-collection.csv", StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(data);
    }

    @PostMapping("/usage/event")
    public WorkspaceApiResponse<UsageEventEntity> recordUsage(@RequestBody WorkspaceDtos.UsageEventRequest request) {
        try { return WorkspaceApiResponse.success(workspaceService.recordUsage(request)); }
        catch (Exception e) { return WorkspaceApiResponse.fail(e.getMessage()); }
    }

    @GetMapping("/usage/summary")
    public WorkspaceApiResponse<WorkspaceDtos.UsageSummaryResponse> usageSummary() {
        try { return WorkspaceApiResponse.success(workspaceService.usageSummary()); }
        catch (Exception e) { return WorkspaceApiResponse.fail(e.getMessage()); }
    }

    @PostMapping(value = "/exif/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public WorkspaceApiResponse<ExifRecordEntity> analyzeExif(@RequestPart("file") MultipartFile file,
                                                              @RequestParam(required = false) Long projectId,
                                                              @RequestParam(required = false) String photoKey) {
        try { return WorkspaceApiResponse.success(workspaceService.analyzeExif(file, projectId, photoKey)); }
        catch (Exception e) { return WorkspaceApiResponse.fail(e.getMessage()); }
    }
}
