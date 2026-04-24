package com.example.photoscore.pojo;


import com.example.photoscore.util.OpenCVUtil;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * 清晰度评分器
 * 参考荷赛奖"技术完美"标准中的清晰度要求
 * 
 * 评估维度：
 * - 整体清晰度（拉普拉斯方差）
 * - 边缘锐度（Sobel梯度）
 * - 细节丰富度（高频分量）
 * 
 * @author PhotoScore Pro Team
 */
@Component
public class ClarityScorer extends BaseScorer {

    private static final double LAPLACIAN_MIDPOINT = 500.0;
    private static final double LAPLACIAN_STEEPNESS = 0.008;
    private static final double SOBEL_MIDPOINT = 80.0;
    private static final double SOBEL_STEEPNESS = 0.05;

    @Override
    public String getScorerName() {
        return "清晰度评分";
    }

    @Override
    public String getCategory() {
        return "TECHNICAL";
    }

    @Override
    public double getWeight() {
        return 0.055;
    }

    @Override
    protected double calculateRawScore(BufferedImage image) {
        Mat mat = OpenCVUtil.bufferedImageToMat(image);
        
        // 1. 拉普拉斯方差（整体清晰度）
        Mat gray = new Mat();
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY);
        
        Mat laplacian = new Mat();
        Imgproc.Laplacian(gray, laplacian, CvType.CV_64F);
        
        MatOfDouble mean = new MatOfDouble();
        MatOfDouble stdDev = new MatOfDouble();
        Core.meanStdDev(laplacian, mean, stdDev);
        
        double laplacianVar = Math.pow(stdDev.get(0, 0)[0], 2);
        double laplacianScore = normalizeSigmoid(laplacianVar, LAPLACIAN_MIDPOINT, LAPLACIAN_STEEPNESS);
        
        // 2. Sobel梯度（边缘锐度）
        Mat sobelX = new Mat();
        Mat sobelY = new Mat();
        Imgproc.Sobel(gray, sobelX, CvType.CV_64F, 1, 0);
        Imgproc.Sobel(gray, sobelY, CvType.CV_64F, 0, 1);
        
        Mat sobelMagnitude = new Mat();
        Core.magnitude(sobelX, sobelY, sobelMagnitude);
        
        Scalar meanSobel = Core.mean(sobelMagnitude);
        double sobelScore = normalizeSigmoid(meanSobel.val[0], SOBEL_MIDPOINT, SOBEL_STEEPNESS);
        
        // 综合评分（拉普拉斯权重0.6，Sobel权重0.4）
        double finalScore = laplacianScore * 0.6 + sobelScore * 0.4;
        
        // 释放资源
        gray.release();
        laplacian.release();
        sobelX.release();
        sobelY.release();
        sobelMagnitude.release();
        
        return finalScore;
    }

    @Override
    protected String generateComment(double rawScore, BufferedImage image) {
        if (rawScore >= 0.85) {
            return "清晰度极佳，边缘锐利，毛发、纹理等细节清晰可辨，无可挑剔。";
        } else if (rawScore >= 0.70) {
            return "清晰度良好，主体清晰，但边缘区域有轻微柔化，建议收缩一档光圈拍摄。";
        } else if (rawScore >= 0.55) {
            return "清晰度尚可，画面整体偏软，可能对焦稍有不实或手持抖动，建议使用三脚架或提高快门速度。";
        } else if (rawScore >= 0.40) {
            return "清晰度不足，画面存在可见模糊，请检查对焦点是否准确落在主体上。";
        } else {
            return "清晰度严重不足，照片已失焦，难以分辨细节，建议重新拍摄。";
        }
    }

    @Override
    protected List<String> generateSuggestions(double rawScore, BufferedImage image) {
        List<String> suggestions = new ArrayList<>();
        if (rawScore >= 0.85) {
            suggestions.add("清晰度极佳，无需额外建议，继续保持拍摄稳定性。");
        } else if (rawScore >= 0.70) {
            suggestions.add("边缘有轻微柔化，建议收缩一档光圈（如从 f/2.8 收至 f/4）以提升边缘画质。");
            suggestions.add("如果手持拍摄，可尝试提高快门速度至安全快门以上。");
        } else if (rawScore >= 0.55) {
            suggestions.add("画面整体偏软，可能对焦不实或轻微手抖。建议使用三脚架，或开启镜头防抖功能。");
            suggestions.add("检查对焦点是否准确落在主体上，必要时使用单点对焦。");
        } else if (rawScore >= 0.40) {
            suggestions.add("清晰度不足，画面存在可见模糊。请确保快门速度不低于焦距的倒数（如 50mm 镜头使用 1/50s 以上）。");
            suggestions.add("弱光环境下可适当提高 ISO 以保证快门速度，或使用补光设备。");
        } else {
            suggestions.add("照片严重失焦，建议重新拍摄，确保对焦成功后再按下快门。");
        }
        return suggestions;
    }
}