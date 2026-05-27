package com.example.photoscore.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // 让 https://photo-score.onrender.com 直接显示 static/index.html
        registry.addViewController("/").setViewName("forward:/index.html");
    }
}