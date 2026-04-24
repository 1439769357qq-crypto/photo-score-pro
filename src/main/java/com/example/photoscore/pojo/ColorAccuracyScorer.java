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
            suggestions.add("色彩准确自然，白平衡设置正确。可根据创作意图适当微调饱和度。");
        } else if (rawScore >= 0.60) {
            suggestions.add("轻微偏色，建议在后期软件中校正白平衡，或使用相机内的白平衡偏移功能。");
            suggestions.add("可适当增加饱和度或对比度，提升色彩表现力。");
        } else if (rawScore >= 0.40) {
            suggestions.add("存在明显偏色（偏蓝或偏黄），请检查白平衡设置是否匹配当前光源（如日光、钨丝灯等）。");
            suggestions.add("建议使用自定义白平衡，或拍摄 RAW 格式以便后期无损调整。");
        } else {
            suggestions.add("色彩失真严重，可能是白平衡设置错误或光源复杂导致。建议使用灰卡手动校准白平衡。");
        }
        return suggestions;
    }
}