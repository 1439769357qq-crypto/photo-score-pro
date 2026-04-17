package com.example.photoscore.pojo;


import org.springframework.stereotype.Component;
import java.awt.image.BufferedImage;

@Component
public class ThemeScorer extends BaseScorer {
    @Override public String getScorerName() { return "主题表达评分"; }
    @Override public String getCategory() { return "AESTHETIC"; }
    @Override public double getWeight() { return 0.130; }

    @Override
    protected double calculateRawScore(BufferedImage image) {
        // 简化版：依赖其他评分器综合，此处返回中等偏上默认值
        return 0.70;
    }

    @Override protected String generateComment(double rawScore, BufferedImage image) {
        return "主题明确度评估：需要结合具体内容综合判断。";
    }
}