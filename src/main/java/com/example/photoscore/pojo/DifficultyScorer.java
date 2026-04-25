package com.example.photoscore.pojo;

import com.example.photoscore.util.OpenCVUtil;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

@Component
public class DifficultyScorer extends BaseScorer {

    @Override
    public String getScorerName() { return "拍摄难度评分"; }
    @Override
    public String getCategory() { return "COMPREHENSIVE"; }
    @Override
    public double getWeight() { return 0.075; }

    @Override
    protected double calculateRawScore(BufferedImage image) {
        Mat mat = null;
        Mat gray = null;
        Mat edges = null;
        try {
            mat = OpenCVUtil.bufferedImageToMat(image);
            gray = new Mat();
            Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY);
            edges = new Mat();
            Imgproc.Canny(gray, edges, 50, 150);
            int edgePixels = Core.countNonZero(edges);
            double edgeDensity = (double) edgePixels / (mat.cols() * mat.rows());
            return normalizeLinear(edgeDensity, 0.02, 0.12);
        } finally {
            safeRelease(edges, gray, mat);
        }
    }

    @Override
    protected String generateComment(double rawScore, BufferedImage image) {
        if (rawScore >= 0.85) {
            return "场景极其复杂，细节极其丰富，对拍摄者的技术、耐心和时机把握要求极高。";
        } else if (rawScore >= 0.75) {
            return "拍摄难度较大，画面元素众多，需要良好的构图与光线控制能力。";
        } else if (rawScore >= 0.65) {
            return "拍摄难度中等偏上，存在一定挑战性，但熟练的摄影者可以驾驭。";
        } else if (rawScore >= 0.50) {
            return "拍摄难度一般，属于常规场景，对技术要求不高。";
        } else {
            return "拍摄难度很低，场景简单，主体突出，随手可拍。";
        }
    }

    @Override
    protected List<String> generateSuggestions(double rawScore, BufferedImage image) {
        List<String> suggestions = new ArrayList<>();
        if (rawScore >= 0.85) {
            suggestions.add("这是一张很有挑战性的照片，能拍出来本身就说明技术不错。继续挑战高难度的场景吧！");
        } else if (rawScore >= 0.75) {
            suggestions.add("拍摄有一定难度，画面元素较多。如果下次想拍更简单的，可以找单一主体练习。");
        } else if (rawScore >= 0.50) {
            suggestions.add("拍摄难度适中，比较适合日常记录。如果想挑战自己，可以试试去拍运动、夜景或者抓拍。");
        } else {
            suggestions.add("场景比较简单，很适合新手练习。对焦、曝光都熟悉了之后，可以逐渐增加难度。");
        }
        return suggestions;
    }
}
