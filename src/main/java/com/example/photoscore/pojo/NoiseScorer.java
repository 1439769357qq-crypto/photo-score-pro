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
            suggestions.add("噪点控制极佳，画面通透干净，无需额外处理，可输出大幅面作品。");
        } else if (rawScore >= 0.70) {
            suggestions.add("暗部存在极轻微噪点，建议在后期软件中对暗部进行选择性降噪。");
            suggestions.add("拍摄时尽量使用低 ISO（如 ISO 100‑400），以减少噪点产生。");
        } else if (rawScore >= 0.55) {
            suggestions.add("可见噪点已开始影响观感，请检查 ISO 是否过高（推荐不超过 800）。");
            suggestions.add("如果必须使用高 ISO，可开启相机机内降噪功能，但注意细节损失。");
            suggestions.add("使用三脚架并延长曝光时间，可有效降低 ISO 需求。");
        } else if (rawScore >= 0.40) {
            suggestions.add("噪点较为明显，画质下降明显。请优先降低 ISO 或增加环境光线。");
            suggestions.add("后期可使用专业降噪插件（如 Topaz DeNoise），但无法完全恢复细节。");
        } else {
            suggestions.add("画面颗粒感严重，建议重新拍摄。确保曝光正确，避免欠曝后强制提亮。");
            suggestions.add("若必须保留此照片，可尝试转换为黑白风格，有时能弱化彩色噪点的观感。");
        }
        return suggestions;
    }
}