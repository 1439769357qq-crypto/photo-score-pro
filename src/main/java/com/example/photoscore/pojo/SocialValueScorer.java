package com.example.photoscore.pojo;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.awt.image.BufferedImage;

@Component
public class SocialValueScorer extends BaseScorer {
    @Autowired(required = false)
    private SemanticAnalyzer semanticAnalyzer;

    @Override public String getScorerName() { return "社会价值评分"; }
    @Override public String getCategory() { return "COMPREHENSIVE"; }
    @Override public double getWeight() { return 0.100; }

    @Override
    protected double calculateRawScore(BufferedImage image) {
        if(semanticAnalyzer != null) {
            try { return semanticAnalyzer.analyzeSocialValue(image); } catch(Exception e) {}
        }
        return 0.55;
    }

    @Override protected String generateComment(double rawScore, BufferedImage image) {
        return "社会价值评估：题材具有一定社会意义。";
    }
}