package com.example.photoscore.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    // 从配置文件中读取上传路径，默认为 ./uploads
    @Value("${photoscore.upload.path:./uploads}")
    private String uploadPath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 将 /uploads/** 的 HTTP 请求映射到本地 uploadPath 目录
        Path absolutePath = Paths.get(uploadPath).toAbsolutePath();
        String location = "file:" + absolutePath.toString().replace("\\", "/") + "/";
        // 注意：location 必须以 "/" 结尾
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(location);
    }
}