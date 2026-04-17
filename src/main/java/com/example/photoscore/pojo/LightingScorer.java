package com.example.photoscore.pojo;


import com.example.photoscore.util.OpenCVUtil;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.imgproc.Imgproc;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;

@Component
public class LightingScorer extends BaseScorer {
    @Override public String getScorerName() { return "用光与色彩评分"; }
    @Override public String getCategory() { return "AESTHETIC"; }
    @Override public double getWeight() { return 0.105; }

    @Override
    protected double calculateRawScore(BufferedImage image) {
        Mat mat = OpenCVUtil.bufferedImageToMat(image);
        Mat gray = new Mat();
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY);
        MatOfDouble mean = new MatOfDouble(), stddev = new MatOfDouble();
        Core.meanStdDev(gray, mean, stddev);
        double avgBright = mean.get(0,0)[0];
        double idealDiff = 1.0 - Math.abs(avgBright - 128) / 128.0;
        double contrast = stddev.get(0,0)[0] / 128.0;
        gray.release(); mat.release();
        return (idealDiff * 0.6 + normalizeSigmoid(contrast, 0.3, 10.0) * 0.4);
    }

    @Override protected String generateComment(double rawScore, BufferedImage image) {
        if(rawScore>=0.8) return "光线运用巧妙，质感与氛围俱佳。";
        else if(rawScore>=0.6) return "光线表现良好。";
        else return "光线平淡或过曝/欠曝，建议调整。";
    }
}