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

@Component
public class StyleScorer extends BaseScorer {

    @Override
    public String getScorerName() { return "风格评分"; }
    @Override
    public String getCategory() { return "COMPREHENSIVE"; }
    @Override
    public double getWeight() { return 0.050; }

    @Override
    protected double calculateRawScore(BufferedImage image) {
        Mat mat = OpenCVUtil.bufferedImageToMat(image);
        Mat gray = new Mat();
        Mat hsv = new Mat();
        Mat saturation = null;
        try {
            Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY);
            MatOfDouble stdDev = new MatOfDouble();
            Core.meanStdDev(gray, new MatOfDouble(), stdDev);
            double contrast = stdDev.get(0, 0)[0] / 128.0;
            Imgproc.cvtColor(mat, hsv, Imgproc.COLOR_BGR2HSV);
            java.util.List<Mat> channels = new ArrayList<>();
            Core.split(hsv, channels);
            saturation = channels.get(1);
            MatOfDouble meanSat = new MatOfDouble();
            Core.meanStdDev(saturation, meanSat, new MatOfDouble());
            double avgSaturation = meanSat.get(0, 0)[0] / 255.0;
            double contrastScore = normalizeSigmoid(contrast, 0.35, 10.0);
            double saturationScore = normalizeSigmoid(avgSaturation, 0.3, 12.0);
            return (contrastScore * 0.5 + saturationScore * 0.5);
        } finally {
            mat.release(); gray.release(); hsv.release();
            if (saturation != null) saturation.release();
        }
    }

    @Override
    protected String generateComment(double rawScore, BufferedImage image) {
        if (rawScore >= 0.85) {
            return "个人风格极其鲜明，色彩、影调、构图具有强烈的作者印记，一眼可辨。";
        } else if (rawScore >= 0.75) {
            return "风格突出，画面呈现出一致的视觉语言，辨识度较高。";
        } else if (rawScore >= 0.65) {
            return "具有一定个人风格，某些视觉元素（如色调、构图习惯）开始形成模式。";
        } else if (rawScore >= 0.50) {
            return "风格尚在探索中，画面较为常规，个人印记不够明显。";
        } else {
            return "无明显个人风格，画面较为随意，建议多尝试不同表现手法。";
        }
    }

    @Override
    protected List<String> generateSuggestions(double rawScore, BufferedImage image) {
        List<String> suggestions = new ArrayList<>();
        if (rawScore >= 0.80) {
            suggestions.add("个人风格鲜明，画面辨识度高。可尝试形成系列作品，强化风格标签。");
        } else if (rawScore >= 0.65) {
            suggestions.add("风格较为明显，建议持续关注同类题材，逐步形成稳定的后期调色思路。");
            suggestions.add("可模仿喜爱的摄影师风格进行练习，再融入个人元素。");
        } else if (rawScore >= 0.50) {
            suggestions.add("风格尚未形成，建议多尝试不同题材和后期风格，找到最适合自己的表达方式。");
        } else {
            suggestions.add("画面较为常规，缺乏个人印记。可尝试打破常规构图或色彩搭配，大胆实验。");
        }
        return suggestions;
    }
}