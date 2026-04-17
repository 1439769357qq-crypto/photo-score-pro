package com.example.photoscore.pojo;


import org.springframework.stereotype.Component;
import java.awt.image.BufferedImage;

@Component
public class StyleScorer extends BaseScorer {
    @Override public String getScorerName() { return "风格评分"; }
    @Override public String getCategory() { return "COMPREHENSIVE"; }
    @Override public double getWeight() { return 0.050; }

    @Override
    protected double calculateRawScore(BufferedImage image) {
        return 0.68;
    }

    @Override protected String generateComment(double rawScore, BufferedImage image) {
        return "风格辨识度评估：具有一定个人风格。";
    }
}