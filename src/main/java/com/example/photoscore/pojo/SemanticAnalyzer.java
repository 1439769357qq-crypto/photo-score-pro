package com.example.photoscore.pojo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.awt.image.BufferedImage;

@Slf4j
@Component
public class SemanticAnalyzer {
    public double analyzeSocialValue(BufferedImage image) {
        // 简化实现：可扩展为真实语义分析模型
        return 0.60;
    }
}
