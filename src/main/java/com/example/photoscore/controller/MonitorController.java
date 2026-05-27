package com.example.photoscore.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class MonitorController {

    private final JdbcTemplate jdbcTemplate;

    @GetMapping("/api/monitor/status")
    public Map<String, Object> status() {
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("status", "UP");
        result.put("service", "photo-score");
        result.put("time", LocalDateTime.now().toString());

        try {
            Integer db = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            result.put("database", db != null && db == 1 ? "UP" : "UNKNOWN");
        } catch (Exception e) {
            result.put("status", "DOWN");
            result.put("database", "DOWN");
            result.put("databaseError", e.getMessage());
        }

        return result;
    }
}