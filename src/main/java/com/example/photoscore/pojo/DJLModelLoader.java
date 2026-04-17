package com.example.photoscore.pojo;

import ai.djl.modality.cv.Image;
import ai.djl.repository.zoo.ZooModel;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;


@Slf4j
@Component
public class DJLModelLoader {
    private ZooModel<Image, float[]> aestheticModel;
    private ZooModel<Image, float[]> technicalModel;

    @PostConstruct
    public void init() {
        // 模型实际加载在NIMAScorer中，此处作为备用
        log.info("DJL环境就绪");
    }

    @PreDestroy
    public void destroy() {}
}