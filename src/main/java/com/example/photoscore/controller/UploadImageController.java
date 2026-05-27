package com.example.photoscore.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

@RestController
public class UploadImageController {

    @Value("${photoscore.upload.path}")
    private String uploadRoot;

    @GetMapping({"/api/uploads/**", "/uploads/**"})
    public ResponseEntity<Resource> getUploadImage(HttpServletRequest request) throws Exception {
        String pattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String pathWithinMapping = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);

        String relativePath = new AntPathMatcher().extractPathWithinPattern(pattern, pathWithinMapping);
        relativePath = URLDecoder.decode(relativePath, StandardCharsets.UTF_8);

        Path root = Paths.get(uploadRoot).toAbsolutePath().normalize();
        Path file = root.resolve(relativePath).normalize();

        System.out.println("[PhotoScore] 图片请求URI: " + request.getRequestURI());
        System.out.println("[PhotoScore] relativePath: " + relativePath);
        System.out.println("[PhotoScore] uploadRoot: " + root);
        System.out.println("[PhotoScore] 映射真实文件: " + file);
        System.out.println("[PhotoScore] 文件是否存在: " + Files.exists(file));

        if (!file.startsWith(root)) {
            return ResponseEntity.badRequest().build();
        }

        if (!Files.exists(file) || !Files.isRegularFile(file)) {
            return ResponseEntity.notFound().build();
        }

        String contentType = Files.probeContentType(file);
        if (contentType == null) {
            String lower = file.getFileName().toString().toLowerCase();
            if (lower.endsWith(".png")) contentType = "image/png";
            else if (lower.endsWith(".webp")) contentType = "image/webp";
            else if (lower.endsWith(".gif")) contentType = "image/gif";
            else contentType = "image/jpeg";
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .contentLength(Files.size(file))
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)))
                .body(new FileSystemResource(file));
    }
}