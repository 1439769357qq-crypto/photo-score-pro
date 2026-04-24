package com.example.photoscore.service;


import com.example.photoscore.util.OpenCVUtil;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;

/**
 * 纯OpenCV场景分类器（无需任何深度学习模型，永不失败）
 */
@Component
public class SceneClassifier {

    public String classify(BufferedImage image) {
        Mat mat = OpenCVUtil.bufferedImageToMat(image);
        Mat hsv = new Mat();
        Imgproc.cvtColor(mat, hsv, Imgproc.COLOR_BGR2HSV);

        // 1. 自然/户外检测法：分析天空和植被的颜色占比
        double natureScore = evaluateNature(hsv, mat);

        // 2. 人物检测法：分析暖色调皮肤区占比
        double skinScore = evaluateSkin(hsv);

        // 3. 建筑/室内检测法：分析直线边缘密度
        double buildingScore = evaluateBuilding(mat);

        // 4. 根据得分判断
        if (buildingScore > 0.4) {
            return "城市建筑";
        } else if (skinScore > 0.3) {
            return "人物特写";
        } else if (natureScore > 0.4) {
            return "自然风景";
        } else {
            Mat gray = new Mat();
            Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY);
            Scalar meanBrightness = Core.mean(gray);
            if (meanBrightness.val[0] < 60) {
                return "夜景";
            }
            return "其他场景";
        }
    }

    // 计算天空(蓝/白)和植被(绿)的占比
    private double evaluateNature(Mat hsv, Mat bgr) {
        double skyCount = 0, greenCount = 0, total = hsv.rows() * hsv.cols();
        for (int y = 0; y < hsv.rows(); y++) {
            for (int x = 0; x < hsv.cols(); x++) {
                double[] pixel = hsv.get(y, x);
                double h = pixel[0], s = pixel[1], v = pixel[2];
                if (v > 80 && s > 30) {
                    if ((h > 90 && h < 150) || (h > 30 && h < 80)) {
                        greenCount++;
                    }
                }
                if (v > 120 && s < 50) {
                    double[] bgrPixel = bgr.get(y, x);
                    double b = bgrPixel[0], g = bgrPixel[1], r = bgrPixel[2];
                    if (b > 150 && g > 150 && r > 150) {
                        skyCount++;
                    }
                }
            }
        }
        return (skyCount + greenCount) / total;
    }

    // 计算皮肤色区占比
    private double evaluateSkin(Mat hsv) {
        double skinCount = 0, total = hsv.rows() * hsv.cols();
        for (int y = 0; y < hsv.rows(); y++) {
            for (int x = 0; x < hsv.cols(); x++) {
                double[] pixel = hsv.get(y, x);
                double h = pixel[0], s = pixel[1], v = pixel[2];
                if (h > 0 && h < 25 && s > 30 && v > 50) {
                    skinCount++;
                }
            }
        }
        return skinCount / total;
    }

    // 计算边缘密度判定建筑/室内
    private double evaluateBuilding(Mat bgr) {
        Mat gray = new Mat();
        Imgproc.cvtColor(bgr, gray, Imgproc.COLOR_BGR2GRAY);
        Mat edges = new Mat();
        Imgproc.Canny(gray, edges, 80, 200);
        int edgePixels = Core.countNonZero(edges);
        double density = (double) edgePixels / (bgr.rows() * bgr.cols());
        return density;
    }
}