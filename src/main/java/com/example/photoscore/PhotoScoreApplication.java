package com.example.photoscore;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@EnableAsync
@MapperScan("com.example.photoscore.mapper")
public class PhotoScoreApplication {
    public static void main(String[] args) {
        SpringApplication.run(PhotoScoreApplication.class, args);
        System.out.println("========================================");
        System.out.println("  专业照片评分系统 PhotoScore Pro 启动成功！");
        System.out.println("  访问地址: http://localhost:8080/api/index.html");
        System.out.println("========================================");
    }
}