package com.example.photoscore.service;


import com.example.photoscore.pojo.ScoringImageContext;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;

/**
 * 纯OpenCV场景分类器（无需任何深度学习模型，永不失败）
 */
@Component
public class SceneClassifier {

    public String classify(BufferedImage image) {
        try (ScoringImageContext context = ScoringImageContext.from(image)) {
            return classify(context);
        }
    }

    public String classify(ScoringImageContext context) {
        Mat mat = context.getBgr();
        Mat hsv = context.getHsv();

        // 1. 自然/户外检测法：分析天空和植被的颜色占比
        double natureScore = evaluateNature(hsv, mat);

        // 2. 人物检测法：分析肤色区、位置和最大连续主体，避免人在风景中被误判为风景
        PersonEvidence personEvidence = evaluatePerson(hsv, mat);

        // 3. 建筑/室内检测法：分析直线边缘密度
        double buildingScore = evaluateBuilding(context);

        // 4. 根据得分判断
        if (personEvidence.isLikelyPerson(natureScore, buildingScore)) {
            return "人物特写";
        } else if (buildingScore > 0.4) {
            return "城市建筑";
        } else if (natureScore > 0.4) {
            return "自然风景";
        } else {
            Scalar meanBrightness = Core.mean(context.getGray());
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

    private PersonEvidence evaluatePerson(Mat hsv, Mat bgr) {
        Mat yCrCb = new Mat();
        Mat hsvSkinLow = new Mat();
        Mat hsvSkinHigh = new Mat();
        Mat hsvSkin = new Mat();
        Mat yCrCbSkin = new Mat();
        Mat skinMask = new Mat();
        Mat kernel = null;
        Mat labels = new Mat();
        Mat stats = new Mat();
        Mat centroids = new Mat();

        try {
            Imgproc.cvtColor(bgr, yCrCb, Imgproc.COLOR_BGR2YCrCb);

            Core.inRange(hsv, new Scalar(0, 20, 35), new Scalar(25, 230, 255), hsvSkinLow);
            Core.inRange(hsv, new Scalar(160, 20, 35), new Scalar(180, 230, 255), hsvSkinHigh);
            Core.bitwise_or(hsvSkinLow, hsvSkinHigh, hsvSkin);

            Core.inRange(yCrCb, new Scalar(35, 133, 77), new Scalar(255, 178, 135), yCrCbSkin);
            Core.bitwise_and(hsvSkin, yCrCbSkin, skinMask);

            kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(3, 3));
            Imgproc.morphologyEx(skinMask, skinMask, Imgproc.MORPH_OPEN, kernel);
            Imgproc.morphologyEx(skinMask, skinMask, Imgproc.MORPH_CLOSE, kernel);

            double totalPixels = Math.max(1.0, skinMask.rows() * skinMask.cols());
            double skinRatio = Core.countNonZero(skinMask) / totalPixels;
            double centralSkinRatio = countCentralSkinRatio(skinMask);
            double upperCenterSkinRatio = countUpperCenterSkinRatio(skinMask);

            int componentCount = Imgproc.connectedComponentsWithStats(skinMask, labels, stats, centroids);
            SkinComponent largest = SkinComponent.empty();
            for (int label = 1; label < componentCount; label++) {
                double area = stats.get(label, Imgproc.CC_STAT_AREA)[0];
                if (area <= largest.area) {
                    continue;
                }

                double centerX = centroids.get(label, 0)[0] / Math.max(1.0, skinMask.cols());
                double centerY = centroids.get(label, 1)[0] / Math.max(1.0, skinMask.rows());
                double width = stats.get(label, Imgproc.CC_STAT_WIDTH)[0];
                double height = stats.get(label, Imgproc.CC_STAT_HEIGHT)[0];
                largest = new SkinComponent(area, centerX, centerY, width, height);
            }

            return new PersonEvidence(skinRatio, centralSkinRatio, upperCenterSkinRatio, largest, totalPixels);
        } finally {
            release(yCrCb, hsvSkinLow, hsvSkinHigh, hsvSkin, yCrCbSkin, skinMask, kernel, labels, stats, centroids);
        }
    }

    private double countCentralSkinRatio(Mat skinMask) {
        int x = (int) Math.round(skinMask.cols() * 0.20);
        int y = (int) Math.round(skinMask.rows() * 0.08);
        int width = Math.max(1, (int) Math.round(skinMask.cols() * 0.60));
        int height = Math.max(1, (int) Math.round(skinMask.rows() * 0.82));
        return countMaskRatio(skinMask, new Rect(x, y, Math.min(width, skinMask.cols() - x), Math.min(height, skinMask.rows() - y)));
    }

    private double countUpperCenterSkinRatio(Mat skinMask) {
        int x = (int) Math.round(skinMask.cols() * 0.25);
        int y = (int) Math.round(skinMask.rows() * 0.08);
        int width = Math.max(1, (int) Math.round(skinMask.cols() * 0.50));
        int height = Math.max(1, (int) Math.round(skinMask.rows() * 0.52));
        return countMaskRatio(skinMask, new Rect(x, y, Math.min(width, skinMask.cols() - x), Math.min(height, skinMask.rows() - y)));
    }

    private double countMaskRatio(Mat mask, Rect rect) {
        Mat region = mask.submat(rect);
        try {
            double area = Math.max(1.0, rect.width * rect.height);
            return Core.countNonZero(region) / area;
        } finally {
            region.release();
        }
    }

    // 计算边缘密度判定建筑/室内
    private double evaluateBuilding(ScoringImageContext context) {
        Mat edges = new Mat();
        try {
            Imgproc.Canny(context.getGray(), edges, 80, 200);
            int edgePixels = Core.countNonZero(edges);
            return (double) edgePixels / (context.getGray().rows() * context.getGray().cols());
        } finally {
            edges.release();
        }
    }

    private void release(Mat... mats) {
        if (mats == null) {
            return;
        }
        for (Mat mat : mats) {
            if (mat != null) {
                mat.release();
            }
        }
    }

    private static class PersonEvidence {
        private final double skinRatio;
        private final double centralSkinRatio;
        private final double upperCenterSkinRatio;
        private final SkinComponent largestComponent;
        private final double totalPixels;

        private PersonEvidence(double skinRatio,
                               double centralSkinRatio,
                               double upperCenterSkinRatio,
                               SkinComponent largestComponent,
                               double totalPixels) {
            this.skinRatio = skinRatio;
            this.centralSkinRatio = centralSkinRatio;
            this.upperCenterSkinRatio = upperCenterSkinRatio;
            this.largestComponent = largestComponent;
            this.totalPixels = totalPixels;
        }

        private boolean isLikelyPerson(double natureScore, double buildingScore) {
            double largestRatio = largestComponent.area / Math.max(1.0, totalPixels);
            boolean centralSubject = largestComponent.centerX >= 0.18
                    && largestComponent.centerX <= 0.82
                    && largestComponent.centerY >= 0.08
                    && largestComponent.centerY <= 0.78;
            double aspectRatio = largestComponent.height <= 0 ? 0 : largestComponent.width / largestComponent.height;
            boolean faceLikeShape = aspectRatio >= 0.45 && aspectRatio <= 2.60;

            double confidence = 0.0;
            if (skinRatio >= 0.020) confidence += 0.25;
            if (skinRatio >= 0.055) confidence += 0.20;
            if (skinRatio >= 0.110) confidence += 0.18;
            if (centralSkinRatio >= 0.030) confidence += 0.18;
            if (upperCenterSkinRatio >= 0.025) confidence += 0.16;
            if (largestRatio >= 0.006) confidence += 0.18;
            if (largestRatio >= 0.020) confidence += 0.14;
            if (centralSubject) confidence += 0.12;
            if (faceLikeShape) confidence += 0.08;

            if (natureScore > 0.55 && skinRatio < 0.018 && largestRatio < 0.010) {
                confidence -= 0.18;
            }
            if (buildingScore > 0.45 && skinRatio < 0.050) {
                confidence -= 0.12;
            }

            return confidence >= 0.58
                    || skinRatio >= 0.080
                    || (centralSubject && faceLikeShape && largestRatio >= 0.010 && upperCenterSkinRatio >= 0.018);
        }
    }

    private static class SkinComponent {
        private final double area;
        private final double centerX;
        private final double centerY;
        private final double width;
        private final double height;

        private SkinComponent(double area, double centerX, double centerY, double width, double height) {
            this.area = area;
            this.centerX = centerX;
            this.centerY = centerY;
            this.width = width;
            this.height = height;
        }

        private static SkinComponent empty() {
            return new SkinComponent(0, 0, 0, 0, 0);
        }
    }
}
