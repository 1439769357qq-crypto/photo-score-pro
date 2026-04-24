package com.example.photoscore.pojo;


import com.example.photoscore.util.OpenCVUtil;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.Rect;
import org.opencv.imgproc.Imgproc;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

@Component
public class NoiseScorer extends BaseScorer {

    private static final double NOISE_MIDPOINT = 8.0;
    private static final double NOISE_STEEPNESS = 0.3;
    private static final int BLOCK_SIZE = 16;

    @Override
    public String getScorerName() { return "噪点控制评分"; }
    @Override
    public String getCategory() { return "TECHNICAL"; }
    @Override
    public double getWeight() { return 0.045; }

    @Override
    protected double calculateRawScore(BufferedImage image) {
        Mat mat = OpenCVUtil.bufferedImageToMat(image);
        Mat gray = new Mat();
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY);
        int width = gray.cols();
        int height = gray.rows();
        double totalVariance = 0;
        int blockCount = 0;
        for (int y = 0; y < height - BLOCK_SIZE; y += BLOCK_SIZE) {
            for (int x = 0; x < width - BLOCK_SIZE; x += BLOCK_SIZE) {
                Rect roi = new Rect(x, y, BLOCK_SIZE, BLOCK_SIZE);
                Mat block = new Mat(gray, roi);
                MatOfDouble mean = new MatOfDouble();
                MatOfDouble stdDev = new MatOfDouble();
                Core.meanStdDev(block, mean, stdDev);
                totalVariance += Math.pow(stdDev.get(0, 0)[0], 2);
                blockCount++;
            }
        }
        double avgVariance = blockCount > 0 ? totalVariance / blockCount : 0;
        double noiseLevel = Math.sqrt(avgVariance);
        double noiseScore = 1.0 - normalizeSigmoid(noiseLevel, NOISE_MIDPOINT, NOISE_STEEPNESS);
        gray.release();
        return Math.max(0.0, Math.min(1.0, noiseScore));
    }

    @Override
    protected String generateComment(double rawScore, BufferedImage image) {
        if (rawScore >= 0.85) return "画面极为干净，几乎没有可见噪点，画质纯净。";
        else if (rawScore >= 0.70) return "噪点控制良好，仅暗部有极轻微噪点，整体画质干净。";
        else if (rawScore >= 0.55) return "存在可见噪点，尤其在暗部区域，但尚在可接受范围。";
        else if (rawScore >= 0.40) return "噪点较为明显，对画质有一定影响，可能是高ISO拍摄所致。";
        else return "噪点严重，画面颗粒感强，画质受损明显。";
    }

    @Override
    protected List<String> generateSuggestions(double rawScore, BufferedImage image) {
        List<String> suggestions = new ArrayList<>();
        if (rawScore >= 0.85) {
            suggestions.add("画面非常干净，噪点控制得很好。继续保持良好的光线环境。");
        } else if (rawScore >= 0.70) {
            suggestions.add("暗部有轻微的颗粒感。下次拍照时，尽量靠近窗户或灯光，让画面更明亮。");
            suggestions.add("有些手机有夜景模式，可以试试，能有效减少噪点。");
        } else if (rawScore >= 0.55) {
            suggestions.add("噪点比较明显了。建议去光线更好的地方拍，或者打开手机闪光灯补光。");
            suggestions.add("后期可以用美图秀秀或醒图里的降噪功能处理一下。");
        } else if (rawScore >= 0.40) {
            suggestions.add("画面颗粒感很强。尽量避免在很暗的环境下拍照。如果必须拍，把手机固定好，用夜景模式。");
        } else {
            suggestions.add("噪点非常严重，画质损失很大。这张照片可能不适合打印或放大，建议在有自然光的时候重拍。");
        }
        return suggestions;
    }
}