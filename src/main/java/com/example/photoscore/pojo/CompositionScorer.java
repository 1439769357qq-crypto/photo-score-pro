package com.example.photoscore.pojo;


import com.example.photoscore.util.OpenCVUtil;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * 构图水平评分器
 * 参考PPA国际摄影大赛"Composition"标准
 * 
 * 评估维度：
 * - 三分法构图（兴趣点位置）
 * - 画面平衡感（视觉重心分布）
 * - 画面简洁度（边缘复杂度）
 * - 对称性（镜像相似度）
 * - 视觉引导性（线条方向）
 * 
 * @author PhotoScore Pro Team
 */
@Component
public class CompositionScorer extends BaseScorer {

    @Override
    public String getScorerName() {
        return "构图水平评分";
    }

    @Override
    public String getCategory() {
        return "AESTHETIC";
    }

    @Override
    public double getWeight() {
        return 0.140;
    }

    @Override
    protected double calculateRawScore(BufferedImage image) {
        Mat mat = OpenCVUtil.bufferedImageToMat(image);
        int width = mat.cols();
        int height = mat.rows();
        
        // 1. 计算视觉重心
        Point visualCenter = calculateVisualCenter(mat);
        
        // 2. 三分法构图评分
        double thirdScore = evaluateRuleOfThirds(visualCenter, width, height);
        
        // 3. 画面简洁度评分（基于边缘密度）
        double simplicityScore = evaluateSimplicity(mat);
        
        // 4. 对称性评分
        double symmetryScore = evaluateSymmetry(mat);
        
        // 5. 画面平衡感评分
        double balanceScore = evaluateBalance(mat, visualCenter, width, height);
        
        // 综合评分
        double[] scores = {thirdScore, simplicityScore, symmetryScore, balanceScore};
        double[] weights = {0.35, 0.25, 0.15, 0.25};
        
        return weightedAverage(scores, weights);
    }

    /**
     * 计算视觉重心
     */
    private Point calculateVisualCenter(Mat mat) {
        Mat gray = new Mat();
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY);
        
        // 使用Sobel边缘检测计算边缘强度作为权重
        Mat sobelX = new Mat();
        Mat sobelY = new Mat();
        Imgproc.Sobel(gray, sobelX, CvType.CV_64F, 1, 0);
        Imgproc.Sobel(gray, sobelY, CvType.CV_64F, 0, 1);
        
        Mat magnitude = new Mat();
        Core.magnitude(sobelX, sobelY, magnitude);
        
        double totalWeight = 0;
        double weightedX = 0;
        double weightedY = 0;
        
        for (int y = 0; y < magnitude.rows(); y++) {
            for (int x = 0; x < magnitude.cols(); x++) {
                double weight = magnitude.get(y, x)[0];
                weightedX += x * weight;
                weightedY += y * weight;
                totalWeight += weight;
            }
        }
        
        gray.release();
        sobelX.release();
        sobelY.release();
        magnitude.release();
        
        if (totalWeight == 0) {
            return new Point(mat.cols() / 2.0, mat.rows() / 2.0);
        }
        return new Point(weightedX / totalWeight, weightedY / totalWeight);
    }

    /**
     * 三分法构图评分
     */
    private double evaluateRuleOfThirds(Point center, int width, int height) {
        // 四个三分点
        Point[] thirdPoints = {
            new Point(width / 3.0, height / 3.0),
            new Point(2.0 * width / 3.0, height / 3.0),
            new Point(width / 3.0, 2.0 * height / 3.0),
            new Point(2.0 * width / 3.0, 2.0 * height / 3.0)
        };
        
        double minDistance = Double.MAX_VALUE;
        for (Point p : thirdPoints) {
            double distance = Math.sqrt(
                Math.pow(center.x - p.x, 2) + Math.pow(center.y - p.y, 2)
            );
            minDistance = Math.min(minDistance, distance);
        }
        
        double maxDistance = Math.sqrt(width * width + height * height) / 2.5;
        return 1.0 - Math.min(1.0, minDistance / maxDistance);
    }

    /**
     * 画面简洁度评分（边缘密度越低越简洁）
     */
    private double evaluateSimplicity(Mat mat) {
        Mat gray = new Mat();
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY);
        
        Mat edges = new Mat();
        Imgproc.Canny(gray, edges, 50, 150);
        
        int edgePixels = Core.countNonZero(edges);
        double edgeDensity = (double) edgePixels / (mat.cols() * mat.rows());
        
        gray.release();
        edges.release();
        
        return 1.0 - normalizeLinear(edgeDensity, 0.01, 0.15);
    }

    /**
     * 对称性评分
     */
    private double evaluateSymmetry(Mat mat) {
        int width = mat.cols();
        int height = mat.rows();
        int halfWidth = width / 2;
        
        Mat leftHalf = new Mat(mat, new Rect(0, 0, halfWidth, height));
        Mat rightHalf = new Mat(mat, new Rect(width - halfWidth, 0, halfWidth, height));
        
        Mat rightFlipped = new Mat();
        Core.flip(rightHalf, rightFlipped, 1);
        
        Mat diff = new Mat();
        Core.absdiff(leftHalf, rightFlipped, diff);
        
        Scalar meanDiff = Core.mean(diff);
        double similarity = 1.0 - (meanDiff.val[0] + meanDiff.val[1] + meanDiff.val[2]) / (255 * 3);
        
        leftHalf.release();
        rightHalf.release();
        rightFlipped.release();
        diff.release();
        
        return similarity;
    }

    /**
     * 画面平衡感评分
     */
    private double evaluateBalance(Mat mat, Point center, int width, int height) {
        double centerX = width / 2.0;
        double centerY = height / 2.0;
        
        double deviation = Math.sqrt(
            Math.pow(center.x - centerX, 2) + Math.pow(center.y - centerY, 2)
        );
        
        double maxDeviation = Math.min(width, height) / 2.0;
        return 1.0 - Math.min(1.0, deviation / maxDeviation * 0.5);
    }

    @Override
    protected String generateComment(double rawScore, BufferedImage image) {
        if (rawScore >= 0.85) {
            return "构图精湛，主体恰好位于黄金分割点，画面平衡且富有层次，引导线运用巧妙。";
        } else if (rawScore >= 0.70) {
            return "构图良好，主体突出，但陪体或背景略有干扰，可尝试轻微裁剪或改变角度。";
        } else if (rawScore >= 0.55) {
            return "构图尚可，但主体位置不够突出，建议尝试三分法，将主体置于交叉点上。";
        } else if (rawScore >= 0.40) {
            return "构图存在不足，画面元素杂乱，缺乏明确视觉中心，建议简化背景或靠近主体。";
        } else {
            return "构图混乱，主体淹没在环境中，建议重新构思画面，明确拍摄意图。";
        }
    }
    @Override
    protected List<String> generateSuggestions(double rawScore, BufferedImage image) {
        List<String> suggestions = new ArrayList<>();
        if (rawScore >= 0.85) {
            suggestions.add("构图很讲究，主体突出，画面平衡。可以多尝试一些创意角度，比如低角度仰拍。");
        } else if (rawScore >= 0.70) {
            suggestions.add("构图不错。稍微注意一下背景有没有多余的东西（比如路人、垃圾桶），可以换个角度避开。");
            suggestions.add("试着打开手机相机设置里的“网格线”或“参考线”，把想突出的东西放在交点或线上。");
        } else if (rawScore >= 0.55) {
            suggestions.add("主体不够突出。往前走两步，或者蹲下来拍，让拍摄对象在画面里占得更大一些。");
            suggestions.add("背景有点杂乱。换个方向，或者用手机的人像模式把背景虚化掉。");
        } else if (rawScore >= 0.40) {
            suggestions.add("画面看起来比较满，不知道重点看哪里。先想清楚最想拍的是什么，把它放在最显眼的位置。");
        } else {
            suggestions.add("构图比较混乱。可以从最简单的开始：找一个有趣的东西，把它放在画面正中间，背景尽量干净。");
        }
        return suggestions;
    }
}