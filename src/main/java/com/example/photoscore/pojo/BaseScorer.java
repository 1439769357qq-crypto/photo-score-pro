package com.example.photoscore.pojo;

import lombok.extern.slf4j.Slf4j;
import org.opencv.core.Mat;

import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 评分器抽象基类
 * 参考荷赛奖五项标准以及PPA国际摄影大赛四维度标准
 */
@Slf4j
public abstract class BaseScorer {

    public abstract String getScorerName();
    public abstract String getCategory();
    public abstract double getWeight();

    protected abstract double calculateRawScore(BufferedImage image);
    protected abstract String generateComment(double rawScore, BufferedImage image);

    protected List<String> generateSuggestions(double rawScore, BufferedImage image) {
        return new ArrayList<>();
    }

    /**
     * 对图像进行评分的公共方法
     */
    public ScoringResult score(BufferedImage image) {
        long startTime = System.currentTimeMillis();
        double rawScore = calculateRawScore(image);
        BigDecimal finalScore = BigDecimal.valueOf(rawScore * 100)
                .setScale(2, RoundingMode.HALF_UP);
        String comment = generateComment(rawScore, image);
        List<String> suggestions = generateSuggestions(rawScore, image);
        long elapsed = System.currentTimeMillis() - startTime;
        log.debug("评分器 [{}] 完成评分: raw={}, score={}, 耗时={}ms",
            getScorerName(), String.format("%.4f", rawScore), finalScore, elapsed);
        return new ScoringResult(getScorerName(), getCategory(), finalScore,
            rawScore, comment, suggestions, elapsed);
    }

    /**
     * 安全释放 OpenCV Mat 资源（防止 NPE）
     */
    protected static void safeRelease(Mat... mats) {
        if (mats == null) return;
        for (Mat mat : mats) {
            if (mat != null) {
                mat.release();
            }
        }
    }

    protected static void safeRelease(List<Mat> mats) {
        if (mats == null) return;
        for (Mat mat : mats) {
            if (mat != null) {
                mat.release();
            }
        }
    }

    protected double normalizeSigmoid(double value, double midpoint, double steepness) {
        return 1.0 / (1.0 + Math.exp(-steepness * (value - midpoint)));
    }

    protected double normalizeLinear(double value, double min, double max) {
        if (max <= min) return 0.5;
        return Math.max(0.0, Math.min(1.0, (value - min) / (max - min)));
    }

    protected double weightedAverage(double[] values, double[] weights) {
        if (values == null || weights == null || values.length != weights.length) return 0.5;
        double sum = 0, weightSum = 0;
        for (int i = 0; i < values.length; i++) {
            sum += values[i] * weights[i];
            weightSum += weights[i];
        }
        return weightSum > 0 ? sum / weightSum : 0.5;
    }
}
