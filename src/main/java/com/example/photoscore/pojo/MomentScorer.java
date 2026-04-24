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
public class MomentScorer extends BaseScorer {

    @Override
    public String getScorerName() { return "瞬间捕捉评分"; }
    @Override
    public String getCategory() { return "AESTHETIC"; }
    @Override
    public double getWeight() { return 0.025; }

    @Override
    protected double calculateRawScore(BufferedImage image) {
        Mat mat = OpenCVUtil.bufferedImageToMat(image);
        Mat gray = new Mat();
        Mat edges = new Mat();
        try {
            Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY);
            MatOfDouble stdDev = new MatOfDouble();
            Core.meanStdDev(gray, new MatOfDouble(), stdDev);
            double contrast = stdDev.get(0, 0)[0] / 128.0;
            Imgproc.Canny(gray, edges, 50, 150);
            int edgePixels = Core.countNonZero(edges);
            double edgeDensity = (double) edgePixels / (mat.cols() * mat.rows());
            double contrastScore = normalizeSigmoid(contrast, 0.4, 8.0);
            double edgeScore = normalizeSigmoid(edgeDensity, 0.03, 50.0);
            return (contrastScore * 0.4 + edgeScore * 0.6);
        } finally {
            mat.release(); gray.release(); edges.release();
        }
    }

    @Override
    protected String generateComment(double rawScore, BufferedImage image) {
        if (rawScore >= 0.85) {
            return "决定性瞬间捕捉精准，画面充满动感与张力，时机把握堪称完美。";
        } else if (rawScore >= 0.75) {
            return "瞬间捕捉出色，主体动作或表情处于最佳状态，画面生动。";
        } else if (rawScore >= 0.65) {
            return "瞬间捕捉良好，画面有一定动态表现力，但未达到最高潮。";
        } else if (rawScore >= 0.50) {
            return "瞬间捕捉一般，画面偏向静态，缺乏令人印象深刻的决定性时刻。";
        } else {
            return "瞬间捕捉较弱，画面平淡，建议等待更精彩的动作或表情出现。";
        }
    }

    @Override
    protected List<String> generateSuggestions(double rawScore, BufferedImage image) {
        List<String> suggestions = new ArrayList<>();
        if (rawScore >= 0.75) {
            suggestions.add("瞬间捕捉能力出色，可尝试连拍模式捕捉更微妙的瞬间变化。");
        } else if (rawScore >= 0.50) {
            suggestions.add("使用高速连拍，从多张照片中挑选表情或动作最到位的一张。");
            suggestions.add("提前预判主体的运动轨迹，半按快门保持对焦，等待最佳时机。");
        } else {
            suggestions.add("拍摄运动物体时，使用快门优先模式，确保足够快的快门速度。");
            suggestions.add("多观察，培养预判能力，在动作发生前就做好拍摄准备。");
        }
        return suggestions;
    }
}