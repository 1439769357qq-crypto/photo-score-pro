package com.example.photoscore.pojo;


import org.springframework.stereotype.Component;
import java.awt.image.BufferedImage;

@Component
public class MomentScorer extends BaseScorer {
    @Override public String getScorerName() { return "瞬间捕捉评分"; }
    @Override public String getCategory() { return "AESTHETIC"; }
    @Override public double getWeight() { return 0.025; }

    @Override
    protected double calculateRawScore(BufferedImage image) {
        return 0.65; // 静态图像难以自动评估瞬间
    }

    @Override protected String generateComment(double rawScore, BufferedImage image) {
        return "决定性瞬间捕捉评估：请结合动态场景判断。";
    }
}