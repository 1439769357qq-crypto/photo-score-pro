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
public class SocialValueScorer extends BaseScorer {

    @Override
    public String getScorerName() { return "社会价值评分"; }
    @Override
    public String getCategory() { return "COMPREHENSIVE"; }
    @Override
    public double getWeight() { return 0.100; }

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
            double baseScore = normalizeLinear(edgeDensity, 0.02, 0.10);
            return Math.min(0.85, baseScore * 1.2);
        } finally {
            mat.release(); gray.release(); edges.release();
        }
    }

    @Override
    protected String generateComment(double rawScore, BufferedImage image) {
        if (rawScore >= 0.80) {
            return "题材具有极强的社会意义，画面承载了重要的时代信息或人文关怀，具备档案价值。";
        } else if (rawScore >= 0.70) {
            return "题材具有一定社会价值，能引发观者对特定社会现象或群体的关注。";
        } else if (rawScore >= 0.55) {
            return "社会价值一般，画面记录了日常场景，缺乏广泛的社会指向性。";
        } else if (rawScore >= 0.40) {
            return "社会价值较低，画面偏向个人审美表达，未触及公共议题。";
        } else {
            return "无明显社会价值，纯粹的个人记录或艺术探索。";
        }
    }

    @Override
    protected List<String> generateSuggestions(double rawScore, BufferedImage image) {
        List<String> suggestions = new ArrayList<>();
        if (rawScore >= 0.70) {
            suggestions.add("这张照片很有记录价值，说不定可以投稿给一些纪实类摄影比赛。");
            suggestions.add("试着为照片写一段简短的文字说明，让看的人更了解背后的故事。");
        } else if (rawScore >= 0.50) {
            suggestions.add("可以尝试多拍一些身边人的生活状态，比如菜市场、街边小店、公园里的老人。");
        } else {
            suggestions.add("照片偏向个人记录，如果想更有社会意义，可以关注一些公共事件或社会现象。");
        }
        return suggestions;
    }
}