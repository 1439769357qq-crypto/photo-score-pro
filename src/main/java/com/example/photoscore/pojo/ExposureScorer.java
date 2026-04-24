package com.example.photoscore.pojo;


import com.example.photoscore.util.OpenCVUtil;
import org.opencv.core.Mat;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfInt;
import org.opencv.imgproc.Imgproc;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * 曝光控制评分器
 * 参考荷赛奖"技术完美"标准中的曝光要求
 * 
 * 评估维度：
 * - 过曝程度（高光溢出比例）
 * - 欠曝程度（阴影死黑比例）
 * - 曝光均匀度（区域亮度方差）
 * 
 * @author PhotoScore Pro Team
 */
@Component
public class ExposureScorer extends BaseScorer {

    private static final double IDEAL_MEAN_BRIGHTNESS = 128.0;
    private static final double OVEREXPOSURE_THRESHOLD = 250;
    private static final double UNDEREXPOSURE_THRESHOLD = 10;

    @Override
    public String getScorerName() {
        return "曝光控制评分";
    }

    @Override
    public String getCategory() {
        return "TECHNICAL";
    }

    @Override
    public double getWeight() {
        return 0.085;
    }

    @Override
    protected double calculateRawScore(BufferedImage image) {
        Mat mat = OpenCVUtil.bufferedImageToMat(image);
        Mat gray = new Mat();
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY);
        
        // 计算直方图
        MatOfInt histSize = new MatOfInt(256);
        MatOfFloat histRange = new MatOfFloat(0, 256);
        Mat hist = new Mat();
        Imgproc.calcHist(java.util.Collections.singletonList(gray), 
            new MatOfInt(0), new Mat(), hist, histSize, histRange, false);
        
        // 统计过曝和欠曝像素比例
        double totalPixels = gray.cols() * gray.rows();
        double overexposedPixels = 0;
        double underexposedPixels = 0;
        double sumBrightness = 0;
        
        for (int i = 0; i < 256; i++) {
            double count = hist.get(i, 0)[0];
            sumBrightness += i * count;
            if (i > OVEREXPOSURE_THRESHOLD) {
                overexposedPixels += count;
            }
            if (i < UNDEREXPOSURE_THRESHOLD) {
                underexposedPixels += count;
            }
        }
        
        double overRatio = overexposedPixels / totalPixels;
        double underRatio = underexposedPixels / totalPixels;
        double avgBrightness = sumBrightness / totalPixels;
        
        // 亮度偏离理想值的程度
        double brightnessDeviation = Math.abs(avgBrightness - IDEAL_MEAN_BRIGHTNESS) / 128.0;
        double brightnessScore = 1.0 - brightnessDeviation;
        
        // 过曝和欠曝惩罚
        double overPenalty = Math.min(1.0, overRatio * 3);
        double underPenalty = Math.min(1.0, underRatio * 3);
        
        double exposureScore = brightnessScore * (1.0 - overPenalty * 0.5 - underPenalty * 0.3);
        
        gray.release();
        
        return Math.max(0.0, Math.min(1.0, exposureScore));
    }

    @Override
    protected String generateComment(double rawScore, BufferedImage image) {
        // 需要额外计算高光/阴影溢出区域（如果之前已计算）
        if (rawScore >= 0.85) {
            return "曝光精准，高光与阴影区域细节均保留完整。";
        } else if (rawScore >= 0.70) {
            return "曝光良好，仅局部有轻微过曝（如天空）或欠曝（如暗部）。";
        } else if (rawScore >= 0.55) {
            return "曝光存在偏差：画面整体偏亮/偏暗，部分区域细节丢失。";
        } else if (rawScore >= 0.40) {
            return "曝光不足或过曝明显，高光溢出或暗部死黑，建议调整曝光补偿。";
        } else {
            return "曝光严重失误，画面大面积过亮或过暗，细节严重损失。";
        }
    }

    @Override
    protected List<String> generateSuggestions(double rawScore, BufferedImage image) {
        List<String> suggestions = new ArrayList<>();
        if (rawScore >= 0.85) {
            suggestions.add("曝光很准确，亮部和暗部细节都保留得很好。继续保持。");
        } else if (rawScore >= 0.70) {
            suggestions.add("局部有点过亮或过暗。拍照时点一下屏幕对焦，然后上下滑动旁边的小太阳图标，可以调整亮度。");
            suggestions.add("如果光线反差太大（比如窗边逆光），可以打开手机的HDR模式试试。");
        } else if (rawScore >= 0.55) {
            suggestions.add("曝光有些偏差。整体偏亮或偏暗。多试几次滑动小太阳，找到一个平衡点。");
            suggestions.add("也可以先按下快门，然后去相册里用编辑功能调一调亮度和阴影。");
        } else if (rawScore >= 0.40) {
            suggestions.add("曝光问题比较明显。可能是测光点不对。点一下画面中最亮和最暗的中间区域，重新对焦测光。");
        } else {
            suggestions.add("曝光失误了，画面惨白或漆黑。建议在光线均匀的环境下重拍，避免正对着强光源。");
        }
        return suggestions;
    }
}