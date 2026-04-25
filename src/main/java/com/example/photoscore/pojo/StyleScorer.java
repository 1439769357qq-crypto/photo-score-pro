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
        Mat mat = null;
        Mat gray = null;
        Mat hsv = null;
        Mat saturation = null;
        List<Mat> channels = null;

        try {
            mat = OpenCVUtil.bufferedImageToMat(image);
            gray = new Mat();
            Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY);

            MatOfDouble stdDev = new MatOfDouble();
            Core.meanStdDev(gray, new MatOfDouble(), stdDev);
            double contrast = stdDev.get(0, 0)[0] / 128.0;

            hsv = new Mat();
            Imgproc.cvtColor(mat, hsv, Imgproc.COLOR_BGR2HSV);
            channels = new ArrayList<>();
            Core.split(hsv, channels);
            saturation = channels.get(1);

            MatOfDouble meanSat = new MatOfDouble();
            Core.meanStdDev(saturation, meanSat, new MatOfDouble());
            double avgSaturation = meanSat.get(0, 0)[0] / 255.0;

            double contrastScore = normalizeSigmoid(contrast, 0.35, 10.0);
            double saturationScore = normalizeSigmoid(avgSaturation, 0.3, 12.0);
            return (contrastScore * 0.5 + saturationScore * 0.5);
        } finally {
            safeRelease(saturation);
            safeRelease(channels);
            safeRelease(gray, hsv, mat);
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
            suggestions.add("个人风格很明显，画面很有辨识度。可以试着把同风格的照片拼成九宫格发朋友圈。");
        } else if (rawScore >= 0.65) {
            suggestions.add("有一些自己的风格了。可以确定一个喜欢的色调，以后拍照都用同一个滤镜。");
            suggestions.add("多看看你关注的一些摄影博主，模仿他们的调色，慢慢找到自己的感觉。");
        } else if (rawScore >= 0.50) {
            suggestions.add("风格还没完全形成。可以多试试不同的滤镜和修图App，比如醒图、VSCO、Lightroom。");
        } else {
            suggestions.add("照片看起来比较随意。下次拍照前可以想一想：我想让这张照片传达什么感觉？是温暖、清冷、还是复古？然后按这个方向去调色。");
        }
        return suggestions;
    }
}
