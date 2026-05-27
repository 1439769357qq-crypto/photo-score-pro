package com.example.photoscore.config;

import jakarta.annotation.PostConstruct;
import nu.pattern.OpenCV;
import org.springframework.context.annotation.Configuration;


@Configuration
public class OpenCVConfig {
    @PostConstruct
    public void init() {
        OpenCV.loadLocally();
        System.out.println("OpenCV 初始化成功");
    }
}