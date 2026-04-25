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
public class ToneScorer extends BaseScorer {

    @Override
    public String getScorerName() { return "影调表现评分"; }
    @Override
    public String getCategory() { return "TECHNICAL"; }
    @Override
    public double getWeight() { return 0.050; }

    @Override
    protected double calculateRawScore(BufferedImage image) {
        Mat mat = null;
        Mat gray = null;
        try {
            mat = OpenCVUtil.bufferedImageToMat(image);
            gray = new Mat();
            Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY);
            MatOfDouble stdDev = new MatOfDouble();
            Core.meanStdDev(gray, new MatOfDouble(), stdDev);
            double contrast = stdDev.get(0, 0)[0] / 128.0;
            return normalizeSigmoid(contrast, 0.4, 8.0);
        } finally {
            safeRelease(gray, mat);
        }
    }

    @Override
    protected String generateComment(double rawScore, BufferedImage image) {
        if (rawScore >= 0.80) return "影调丰富，层次感强，从亮部到暗部过渡自然。";
        else if (rawScore >= 0.65) return "影调表现良好，画面有一定立体感。";
        else if (rawScore >= 0.50) return "影调一般，画面略显扁平，缺乏层次。";
        else return "影调平淡，高光与暗部区分不明显，画面缺少体积感。";
    }

    @Override
    protected List<String> generateSuggestions(double rawScore, BufferedImage image) {
        List<String> suggestions = new ArrayList<>();
        if (rawScore >= 0.80) {
            suggestions.add("影调很有层次感，从亮部到暗部过渡得很自然。可以打印出来装裱。");
        } else if (rawScore >= 0.65) {
            suggestions.add("影调不错。如果想加强立体感，可以在后期稍微拉一下对比度，或者试试黑白滤镜。");
        } else if (rawScore >= 0.50) {
            suggestions.add("画面有点平，缺少立体感。拍照时尽量利用侧光（光线从侧面照过来），让物体有明暗面。");
            suggestions.add("后期可以用手机自带的编辑工具，增强一下对比度和锐度。");
        } else {
            suggestions.add("影调很平。尽量在早上或傍晚拍照，这时候光线有角度，容易拍出立体感。");
        }
        return suggestions;
    }
}
