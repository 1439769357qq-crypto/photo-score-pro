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
public class ThemeScorer extends BaseScorer {

    @Override
    public String getScorerName() { return "主题表达评分"; }
    @Override
    public String getCategory() { return "AESTHETIC"; }
    @Override
    public double getWeight() { return 0.130; }

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
            return normalizeSigmoid(edgeDensity, 0.04, 40.0);
        } finally {
            mat.release(); gray.release(); edges.release();
        }
    }

    @Override
    protected String generateComment(double rawScore, BufferedImage image) {
        if (rawScore >= 0.90) {
            return "主题极其鲜明，主体占据绝对视觉中心，无任何干扰元素，表达意图清晰有力。";
        } else if (rawScore >= 0.80) {
            return "主题明确突出，主体在画面中占据主导地位，次要元素未分散注意力。";
        } else if (rawScore >= 0.70) {
            return "主题表达清晰，主体较为突出，存在轻微干扰但未影响整体表达。";
        } else if (rawScore >= 0.60) {
            return "主题基本可辨，但主体不够突出，背景或陪体有一定干扰。";
        } else if (rawScore >= 0.45) {
            return "主题表达较弱，画面元素较多，视觉焦点不集中，观者可能困惑。";
        } else {
            return "主题模糊，缺乏明确的拍摄意图，画面杂乱，建议重新构思拍摄对象。";
        }
    }

    @Override
    protected List<String> generateSuggestions(double rawScore, BufferedImage image) {
        List<String> suggestions = new ArrayList<>();
        if (rawScore >= 0.80) {
            suggestions.add("主题非常清晰，一眼就能看出你想表达什么。继续保持这种简洁有力的构图。");
        } else if (rawScore >= 0.60) {
            suggestions.add("主题基本突出，但周围有些东西分散注意力。试试换个角度，或者把不想要的东西裁剪掉。");
            suggestions.add("拍照时心里想一个问题：我最想让看照片的人第一眼看到什么？然后把它放在最显眼的位置。");
        } else {
            suggestions.add("主题不够明确。可以先确定一个主体，然后靠近它，让它占满大部分画面。");
            suggestions.add("多用手机的人像模式虚化背景，能让主体更突出。");
        }
        return suggestions;
    }
}