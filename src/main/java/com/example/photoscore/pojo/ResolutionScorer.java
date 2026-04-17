package com.example.photoscore.pojo;


import org.springframework.stereotype.Component;
import java.awt.image.BufferedImage;

@Component
public class ResolutionScorer extends BaseScorer {
    private static final long IDEAL_PIXELS = 12_000_000L; // 12MP
    @Override public String getScorerName() { return "分辨率评分"; }
    @Override public String getCategory() { return "TECHNICAL"; }
    @Override public double getWeight() { return 0.020; }

    @Override
    protected double calculateRawScore(BufferedImage image) {
        long pixels = (long) image.getWidth() * image.getHeight();
        return Math.min(1.0, pixels / (double) IDEAL_PIXELS);
    }

    @Override protected String generateComment(double rawScore, BufferedImage image) {
        long mp = (long)(image.getWidth() * image.getHeight() / 1_000_000.0);
        return String.format("分辨率约 %dMP，%s", mp, rawScore>=0.8?"满足高画质需求":"建议使用更高像素拍摄");
    }
}