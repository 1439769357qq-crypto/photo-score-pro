package com.example.photoscore.pojo;

import lombok.extern.slf4j.Slf4j;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 评分器抽象基类
 * 参考荷赛奖五项标准（题材重大、瞬间动人、技术完美、拍摄艰难、社会效果显著）
 * 以及PPA国际摄影大赛四维度标准（构图、冲击力、风格、技术卓越）
 * 
 * @author PhotoScore Pro Team
 * @version 2.0.0
 */
@Slf4j
public abstract class BaseScorer {

    /**
     * 获取评分器名称
     */
    public abstract String getScorerName();

    /**
     * 获取评分器类别（TECHNICAL/AESTHETIC/COMPREHENSIVE）
     */
    public abstract String getCategory();

    /**
     * 获取权重
     */
    public abstract double getWeight();

    /**
     * 执行评分计算的核心方法
     * @param image 待评分的BufferedImage对象
     * @return 标准化的评分（0.0 - 1.0）
     */
    protected abstract double calculateRawScore(BufferedImage image);

    /**
     * 根据原始分数生成具体的评分评语
     */
    protected abstract String generateComment(double rawScore, BufferedImage image);

    /**
     * 生成改进建议
     */
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
     * 将数值映射到[0,1]区间（使用S型曲线）
     */
    protected double normalizeSigmoid(double value, double midpoint, double steepness) {
        return 1.0 / (1.0 + Math.exp(-steepness * (value - midpoint)));
    }

    /**
     * 线性归一化
     */
    protected double normalizeLinear(double value, double min, double max) {
        if (max <= min) return 0.5;
        return Math.max(0.0, Math.min(1.0, (value - min) / (max - min)));
    }

    /**
     * 计算加权平均分
     */
    protected double weightedAverage(double[] values, double[] weights) {
        if (values.length != weights.length) return 0.5;
        double sum = 0, weightSum = 0;
        for (int i = 0; i < values.length; i++) {
            sum += values[i] * weights[i];
            weightSum += weights[i];
        }
        return weightSum > 0 ? sum / weightSum : 0.5;
    }
}