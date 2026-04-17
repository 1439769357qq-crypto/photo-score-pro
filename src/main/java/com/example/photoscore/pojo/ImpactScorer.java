package com.example.photoscore.pojo;


import com.example.photoscore.util.OpenCVUtil;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.imgproc.Imgproc;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * 冲击力评分器
 * 参考PPA国际摄影大赛"Impact"标准及荷赛奖"瞬间动人"标准
 * 
 * 评估维度：
 * - 对比度强度
 * - 色彩饱和度
 * - 边缘强度（视觉张力）
 * - 主体突出度
 * 
 * @author PhotoScore Pro Team
 */
@Component
public class ImpactScorer extends BaseScorer {

    @Override
    public String getScorerName() {
        return "冲击力评分";
    }

    @Override
    public String getCategory() {
        return "COMPREHENSIVE";
    }

    @Override
    public double getWeight() {
        return 0.075;
    }

    @Override
    protected double calculateRawScore(BufferedImage image) {
        Mat mat = OpenCVUtil.bufferedImageToMat(image);
        Mat gray = new Mat();
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY);
        
        // 1. 对比度强度
        MatOfDouble mean = new MatOfDouble();
        MatOfDouble stdDev = new MatOfDouble();
        Core.meanStdDev(gray, mean, stdDev);
        double contrast = stdDev.get(0, 0)[0] / 128.0;
        double contrastScore = normalizeSigmoid(contrast, 0.4, 10.0);
        
        // 2. 色彩饱和度
        Mat hsv = new Mat();
        Imgproc.cvtColor(mat, hsv, Imgproc.COLOR_BGR2HSV);
        
        java.util.List<Mat> hsvChannels = new ArrayList<>();
        Core.split(hsv, hsvChannels);
        Mat saturation = hsvChannels.get(1);
        
        MatOfDouble meanSat = new MatOfDouble();
        Core.meanStdDev(saturation, meanSat, new MatOfDouble());
        double avgSaturation = meanSat.get(0, 0)[0] / 255.0;
        double saturationScore = normalizeSigmoid(avgSaturation, 0.3, 12.0);
        
        // 3. 边缘强度（视觉张力）
        Mat edges = new Mat();
        Imgproc.Canny(gray, edges, 50, 150);
        int edgePixels = Core.countNonZero(edges);
        double edgeDensity = (double) edgePixels / (mat.cols() * mat.rows());
        double edgeScore = normalizeSigmoid(edgeDensity, 0.04, 40.0);
        
        // 综合评分
        double[] scores = {contrastScore, saturationScore, edgeScore};
        double[] weights = {0.35, 0.35, 0.30};
        
        gray.release();
        hsv.release();
        edges.release();
        
        return weightedAverage(scores, weights);
    }

    @Override
    protected String generateComment(double rawScore, BufferedImage image) {
        if (rawScore >= 0.85) {
            return "视觉冲击力极强，画面张力十足，能在第一时间抓住观者目光。";
        } else if (rawScore >= 0.70) {
            return "冲击力良好，画面具有较强的视觉吸引力。";
        } else if (rawScore >= 0.55) {
            return "冲击力一般，画面有一定的视觉效果但不够突出。";
        } else if (rawScore >= 0.40) {
            return "冲击力偏弱，画面略显平淡，缺乏视觉张力。";
        } else {
            return "冲击力很弱，画面难以引起观者注意。";
        }
    }

    @Override
    protected List<String> generateSuggestions(double rawScore, BufferedImage image) {
        List<String> suggestions = new ArrayList<>();
        if (rawScore < 0.55) {
            suggestions.add("增强画面对比度，让明暗关系更强烈");
            suggestions.add("提高色彩饱和度或使用色彩对比增强视觉效果");
            suggestions.add("拉近与被摄主体的距离，增强画面冲击力");
        }
        return suggestions;
    }
}