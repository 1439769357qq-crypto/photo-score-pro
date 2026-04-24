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
        Mat mat = OpenCVUtil.bufferedImageToMat(image);
        Mat gray = new Mat();
        Mat edges = new Mat();
        try {
            Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY);
            Imgproc.Canny(gray, edges, 50, 150);
            int edgePixels = Core.countNonZero(edges);
            double edgeDensity = (double) edgePixels / (mat.cols() * mat.rows());
            return normalizeLinear(edgeDensity, 0.02, 0.12);
        } finally {
            mat.release(); gray.release(); edges.release();
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
            suggestions.add("面对复杂场景能成功出片，证明技术扎实。可尝试更极端的拍摄环境挑战自我。");
        } else if (rawScore >= 0.65) {
            suggestions.add("多练习在复杂光线或杂乱背景下的拍摄，提升应变能力。");
        } else {
            suggestions.add("尝试寻找更具挑战性的拍摄题材，突破舒适区。");
        }
        return suggestions;
    }
}