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
public class ColorAccuracyScorer extends BaseScorer {

    @Override
    public String getScorerName() {
        return "色彩准确度评分";
    }

    @Override
    public String getCategory() {
        return "TECHNICAL";
    }

    @Override
    public double getWeight() {
        return 0.045;
    }

    @Override
    protected double calculateRawScore(BufferedImage image) {
        Mat mat = OpenCVUtil.bufferedImageToMat(image);
        Mat hsv = new Mat();
        Mat saturation = null;
        try {
            Imgproc.cvtColor(mat, hsv, Imgproc.COLOR_BGR2HSV);
            List<Mat> channels = new ArrayList<>();
            Core.split(hsv, channels);
            saturation = channels.get(1);
            MatOfDouble mean = new MatOfDouble();
            Core.meanStdDev(saturation, mean, new MatOfDouble());
            double avgSat = mean.get(0, 0)[0] / 255.0;
            return normalizeSigmoid(avgSat, 0.35, 12.0);
        } finally {
            mat.release();
            hsv.release();
            if (saturation != null) saturation.release();
        }
    }

    @Override
    protected String generateComment(double rawScore, BufferedImage image) {
        if (rawScore >= 0.80) {
            return "色彩准确自然，白平衡正确，色彩还原度高。";
        } else if (rawScore >= 0.60) {
            return "色彩基本准确，轻微偏色或饱和度偏差，在可接受范围内。";
        } else if (rawScore >= 0.40) {
            return "色彩存在一定偏差，可能偏冷或偏暖，建议校正白平衡。";
        } else {
            return "色彩偏差明显，饱和度失真，建议检查白平衡设置或后期校正。";
        }
    }

    @Override
    protected List<String> generateSuggestions(double rawScore, BufferedImage image) {
        List<String> suggestions = new ArrayList<>();
        if (rawScore >= 0.80) {
            suggestions.add("颜色看起来很自然，白平衡很准。如果想让照片更有氛围，可以试试手机自带的滤镜。");
        } else if (rawScore >= 0.60) {
            suggestions.add("颜色稍微偏蓝或偏黄。打开手机相机，找到色温或白平衡选项，手动调一调。");
            suggestions.add("有些手机在专业模式里可以直接调整色温值，往左更冷，往右更暖。");
        } else if (rawScore >= 0.40) {
            suggestions.add("颜色偏差比较大。看看是不是开了什么特效或滤镜，关掉再拍试试。");
            suggestions.add("后期可以用修图软件调整色温，让肤色或天空看起来更自然。");
        } else {
            suggestions.add("颜色非常奇怪，可能是在混合光源下拍的（比如日光灯+阳光）。试试只在一个主光源下拍。");
        }
        return suggestions;
    }
}