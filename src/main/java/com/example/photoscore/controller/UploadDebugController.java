package com.example.photoscore.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@RestController
public class UploadDebugController {

    @Value("${photoscore.upload.path}")
    private String uploadRoot;

    @GetMapping("/api/debug/check-image")
    public Map<String, Object> checkImage(@RequestParam String path) throws Exception {
        String cleanPath = path;

        cleanPath = cleanPath.replaceFirst("^https?://[^/]+", "");
        cleanPath = cleanPath.replaceFirst("^/api/uploads/", "");
        cleanPath = cleanPath.replaceFirst("^/uploads/", "");
        cleanPath = cleanPath.replaceFirst("^/+", "");

        Path root = Paths.get(uploadRoot).toAbsolutePath().normalize();
        Path file = root.resolve(cleanPath).normalize();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("uploadRoot", root.toString());
        result.put("inputPath", path);
        result.put("relativePath", cleanPath);
        result.put("mappedFile", file.toString());
        result.put("exists", Files.exists(file));
        result.put("isRegularFile", Files.isRegularFile(file));
        result.put("fileSize", Files.exists(file) ? Files.size(file) : null);

        return result;
    }

    @GetMapping("/api/debug/uploads")
    public Map<String, Object> debugUploads() throws Exception {
        Path root = Paths.get(uploadRoot).toAbsolutePath().normalize();

        List<String> samples = new ArrayList<>();
        if (Files.exists(root)) {
            try (var stream = Files.walk(root, 6)) {
                stream
                        .filter(Files::isRegularFile)
                        .limit(50)
                        .forEach(p -> samples.add(root.relativize(p).toString().replace("\\", "/")));
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("uploadRoot", root.toString());
        result.put("exists", Files.exists(root));
        result.put("isDirectory", Files.isDirectory(root));
        result.put("sampleFiles", samples);
        return result;
    }
}