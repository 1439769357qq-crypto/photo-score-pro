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
    @Override public String getScorerName() { return "色彩准确度评分"; }
    @Override public String getCategory() { return "TECHNICAL"; }
    @Override public double getWeight() { return 0.045; }

    @Override
    protected double calculateRawScore(BufferedImage image) {
        Mat mat = OpenCVUtil.bufferedImageToMat(image);
        Mat hsv = new Mat();
        Imgproc.cvtColor(mat, hsv, Imgproc.COLOR_BGR2HSV);
        List<Mat> channels = new ArrayList<>();
        Core.split(hsv, channels);
        Mat saturation = channels.get(1);
        MatOfDouble mean = new MatOfDouble();
        MatOfDouble stddev = new MatOfDouble();
        Core.meanStdDev(saturation, mean, stddev);
        double avgSat = mean.get(0,0)[0] / 255.0;
        double score = normalizeSigmoid(avgSat, 0.35, 12.0);
        saturation.release(); hsv.release(); mat.release();
        return score;
    }

    @Override
    protected String generateComment(double rawScore, BufferedImage image) {
        if (rawScore >= 0.80) {
            return "色彩准确自然，白平衡正确，色彩还原度高。";
        } else if (rawScore >= 0.60) {
            return "色彩基本准确，轻微偏冷或偏暖，可在后期中微调。";
        } else if (rawScore >= 0.40) {
            return "色彩存在偏色（偏蓝/偏黄），建议校正白平衡。";
        } else {
            return "色彩失真明显，饱和度异常，建议检查白平衡设置。";
        }
    }
}