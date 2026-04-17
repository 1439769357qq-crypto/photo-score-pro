package com.example.photoscore.pojo;


import com.example.photoscore.util.OpenCVUtil;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.imgproc.Imgproc;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;

@Component
public class ToneScorer extends BaseScorer {
    @Override public String getScorerName() { return "影调表现评分"; }
    @Override public String getCategory() { return "TECHNICAL"; }
    @Override public double getWeight() { return 0.050; }

    @Override
    protected double calculateRawScore(BufferedImage image) {
        Mat mat = OpenCVUtil.bufferedImageToMat(image);
        Mat gray = new Mat();
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY);
        MatOfDouble mean = new MatOfDouble(), stddev = new MatOfDouble();
        Core.meanStdDev(gray, mean, stddev);
        double contrast = stddev.get(0,0)[0] / 128.0;
        double contrastScore = normalizeSigmoid(contrast, 0.4, 8.0);
        gray.release(); mat.release();
        return contrastScore;
    }

    @Override protected String generateComment(double rawScore, BufferedImage image) {
        if(rawScore>=0.8) return "影调丰富，层次感强。";
        else if(rawScore>=0.6) return "影调表现良好。";
        else return "影调平淡，缺乏层次。";
    }
}