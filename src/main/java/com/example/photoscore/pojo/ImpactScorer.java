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
public class ImpactScorer extends BaseScorer {

    @Override
    public String getScorerName() { return "冲击力评分"; }
    @Override
    public String getCategory() { return "COMPREHENSIVE"; }
    @Override
    public double getWeight() { return 0.075; }

    @Override
    protected double calculateRawScore(BufferedImage image) {
        Mat mat = OpenCVUtil.bufferedImageToMat(image);
        Mat gray = new Mat();
        Mat edges = new Mat();
        Mat hsv = new Mat();
        Mat saturation = null;
        try {
            Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY);
            MatOfDouble stdDev = new MatOfDouble();
            Core.meanStdDev(gray, new MatOfDouble(), stdDev);
            double contrast = stdDev.get(0, 0)[0] / 128.0;
            double contrastScore = normalizeSigmoid(contrast, 0.4, 10.0);
            Imgproc.Canny(gray, edges, 50, 150);
            int edgePixels = Core.countNonZero(edges);
            double edgeDensity = (double) edgePixels / (mat.cols() * mat.rows());
            double edgeScore = normalizeSigmoid(edgeDensity, 0.04, 40.0);
            Imgproc.cvtColor(mat, hsv, Imgproc.COLOR_BGR2HSV);
            java.util.List<Mat> channels = new ArrayList<>();
            Core.split(hsv, channels);
            saturation = channels.get(1);
            MatOfDouble meanSat = new MatOfDouble();
            Core.meanStdDev(saturation, meanSat, new MatOfDouble());
            double avgSaturation = meanSat.get(0, 0)[0] / 255.0;
            double saturationScore = normalizeSigmoid(avgSaturation, 0.3, 12.0);
            return contrastScore * 0.35 + edgeScore * 0.35 + saturationScore * 0.30;
        } finally {
            mat.release(); gray.release(); edges.release(); hsv.release();
            if (saturation != null) saturation.release();
        }
    }

    @Override
    protected String generateComment(double rawScore, BufferedImage image) {
        if (rawScore >= 0.85) {
            return "视觉冲击力极强，画面张力十足，色彩与构图共同营造出强烈的第一印象。";
        } else if (rawScore >= 0.75) {
            return "冲击力出色，主体鲜明，对比强烈，能迅速抓住观者视线。";
        } else if (rawScore >= 0.65) {
            return "冲击力良好，画面有一定的视觉吸引力，但未达到震撼的程度。";
        } else if (rawScore >= 0.55) {
            return "冲击力一般，画面略显平淡，缺乏令人眼前一亮的视觉爆点。";
        } else if (rawScore >= 0.40) {
            return "冲击力偏弱，视觉张力不足，建议通过构图或色彩增强表现力。";
        } else {
            return "冲击力很弱，画面难以引起观者兴趣，建议重新构思拍摄方式。";
        }
    }

    @Override
    protected List<String> generateSuggestions(double rawScore, BufferedImage image) {
        List<String> suggestions = new ArrayList<>();
        if (rawScore >= 0.80) {
            suggestions.add("视觉冲击力极强，画面张力十足。可尝试输出为黑白，进一步增强对比感。");
        } else if (rawScore >= 0.65) {
            suggestions.add("冲击力良好，可适当增加对比度和饱和度，或通过后期添加暗角突出主体。");
            suggestions.add("尝试更独特的拍摄角度（如低角度仰拍或高角度俯拍）以增强视觉新鲜感。");
        } else if (rawScore >= 0.50) {
            suggestions.add("冲击力一般，画面略显平淡。建议使用更广的焦距靠近主体，增强透视感。");
            suggestions.add("利用色彩对比（如冷暖对比）或明暗对比来吸引视线。");
        } else {
            suggestions.add("冲击力较弱，建议寻找更具表现力的瞬间，或通过后期裁剪强化构图。");
        }
        return suggestions;
    }
}