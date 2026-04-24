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
        Mat mat = OpenCVUtil.bufferedImageToMat(image);
        Mat gray = new Mat();
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY);
        MatOfDouble stdDev = new MatOfDouble();
        Core.meanStdDev(gray, new MatOfDouble(), stdDev);
        double contrast = stdDev.get(0, 0)[0] / 128.0;
        double contrastScore = normalizeSigmoid(contrast, 0.4, 8.0);
        gray.release();
        return contrastScore;
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
            suggestions.add("影调层次极为丰富，可尝试制作高品质艺术微喷输出。");
        } else if (rawScore >= 0.65) {
            suggestions.add("影调表现良好，可适当增强暗部反光或高光细节，提升立体感。");
            suggestions.add("使用曲线工具微调，增加中间调的对比度，画面会更生动。");
        } else if (rawScore >= 0.50) {
            suggestions.add("影调较平，建议在后期中增加对比度，拉开明暗差距。");
            suggestions.add("拍摄时可利用侧光或逆光，创造更丰富的光影效果。");
        } else {
            suggestions.add("影调层次缺失，画面扁平。请检查曝光是否过度均匀，或尝试使用渐变滤镜。");
            suggestions.add("后期可通过分离高光/阴影工具重塑立体感。");
        }
        return suggestions;
    }
}