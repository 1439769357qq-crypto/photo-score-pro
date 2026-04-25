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
        Mat mat = null;
        Mat gray = null;
        Mat edges = null;
        try {
            mat = OpenCVUtil.bufferedImageToMat(image);
            gray = new Mat();
            Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY);

            MatOfDouble stdDev = new MatOfDouble();
            Core.meanStdDev(gray, new MatOfDouble(), stdDev);
            double contrast = stdDev.get(0, 0)[0] / 128.0;

            edges = new Mat();
            Imgproc.Canny(gray, edges, 50, 150);
            int edgePixels = Core.countNonZero(edges);
            double edgeDensity = (double) edgePixels / (mat.cols() * mat.rows());

            double contrastScore = normalizeSigmoid(contrast, 0.4, 8.0);
            double edgeScore = normalizeSigmoid(edgeDensity, 0.03, 50.0);
            return (contrastScore * 0.4 + edgeScore * 0.6);
        } finally {
            safeRelease(edges, gray, mat);
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
        if (rawScore >= 0.85) {
            suggestions.add("瞬间抓拍得非常到位！这种决定性的照片很有感染力。");
        } else if (rawScore >= 0.65) {
            suggestions.add("抓拍能力不错。如果想拍更动感的画面，可以试试按住快门连拍，然后从里面挑最好的一张。");
        } else {
            suggestions.add("画面比较静态。下次拍运动或人物时，提前把手机举起来，预判动作，用连拍去捕捉最高潮的时刻。");
            suggestions.add("在光线好的时候，快门速度更快，更容易捕捉清晰的动态瞬间。");
        }
        return suggestions;
    }
}
