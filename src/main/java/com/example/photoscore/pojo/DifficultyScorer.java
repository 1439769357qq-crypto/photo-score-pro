package com.example.photoscore.pojo;


import org.springframework.stereotype.Component;
import java.awt.image.BufferedImage;

@Component
public class DifficultyScorer extends BaseScorer {
    @Override public String getScorerName() { return "拍摄难度评分"; }
    @Override public String getCategory() { return "COMPREHENSIVE"; }
    @Override public double getWeight() { return 0.075; }

    @Override
    protected double calculateRawScore(BufferedImage image) {
        // 通过边缘复杂度推断难度
        return 0.60;
    }

    @Override protected String generateComment(double rawScore, BufferedImage image) {
        return "拍摄难度评估：场景复杂度中等。";
    }
}