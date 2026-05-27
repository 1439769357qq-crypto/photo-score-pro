package com.example.photoscore.config;

import ai.djl.engine.Engine;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;


@Slf4j
@Configuration
public class DJLConfig {
    @PostConstruct
    public void init() {
        try {
            Engine.getInstance();
            log.info("DJL引擎初始化成功: {}", Engine.getInstance().getEngineName());
        } catch (Exception e) {
            log.warn("DJL引擎初始化失败，AI评分功能将受限: {}", e.getMessage());
        }
    }
}