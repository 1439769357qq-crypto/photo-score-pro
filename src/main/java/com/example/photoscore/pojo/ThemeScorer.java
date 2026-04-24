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
            suggestions.add("主题表达出色，可尝试系列化创作，强化个人风格。");
        } else if (rawScore >= 0.60) {
            suggestions.add("建议使用更大光圈虚化背景，或改变拍摄角度以简化画面。");
            suggestions.add("将主体置于画面黄金分割点，可有效增强主题表达。");
        } else {
            suggestions.add("拍摄前先明确一个主体，围绕它安排构图，避免纳入无关元素。");
            suggestions.add("尝试靠近主体，使其在画面中占据更大比例。");
        }
        return suggestions;
    }
}