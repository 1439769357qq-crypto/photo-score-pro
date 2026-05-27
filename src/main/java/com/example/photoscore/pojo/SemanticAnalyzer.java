package com.example.photoscore.pojo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * 语义价值分析器
 *
 * 说明：
 * 1. 当前版本不依赖深度学习语义模型，采用轻量级图像启发式规则。
 * 2. 输出范围：0.00 - 1.00。
 * 3. 主要用于“社会价值评分 / 记录价值评分”的辅助判断。
 *
 * 评分倾向：
 * - 普通风景、普通静物：较低
 * - 人像、生活记录、公共空间、建筑场景：中等
 * - 人群、标识文字、交通/纪实/公共事件感明显：较高
 *
 * 注意：
 * 这不是严格语义识别模型，无法真正理解图片内容。
 * 如果后续接入豆包/千问/CLIP/目标检测模型，可替换这里的规则逻辑。
 */
@Slf4j
@Component
public class SemanticAnalyzer {

    /**
     * 分析照片社会价值 / 记录价值。
     *
     * @param image 输入图片
     * @return 0.00 - 1.00
     */
    public double analyzeSocialValue(BufferedImage image) {
        if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
            return 0.35;
        }

        try {
            BufferedImage small = resizeForAnalysis(image, 360);

            ImageStats stats = analyzeImageStats(small);

            double humanScore = estimateHumanPresence(stats);
            double documentScore = estimateDocumentaryValue(stats);
            double publicSceneScore = estimatePublicSceneValue(stats);
            double naturePenalty = estimatePureNaturePenalty(stats);
            double qualityPenalty = estimateLowQualityPenalty(stats);

            /*
             * 基础分不要太低。
             * 普通照片也有一定记录意义，但不能动不动给到高社会价值。
             */
            double score = 0.36;

            // 人物、生活记录、人群感
            score += humanScore * 0.22;

            // 标识、文字、牌匾、屏幕、公告等纪实信息
            score += documentScore * 0.24;

            // 建筑、城市、交通、室内公共空间等公共场景
            score += publicSceneScore * 0.18;

            // 画面结构复杂、边缘丰富，通常更像纪实场景
            score += stats.edgeDensity * 0.12;

            // 纯自然风景社会价值通常低于纪实照片
            score -= naturePenalty * 0.18;

            // 画质太差会降低记录可用性
            score -= qualityPenalty * 0.10;

            return clamp(score, 0.05, 0.98);
        } catch (Exception e) {
            log.warn("语义价值分析失败，使用默认社会价值分: {}", e.getMessage());
            return 0.60;
        }
    }

    /**
     * 粗略判断是否更像人物 / 人像 / 生活记录。
     */
    private double estimateHumanPresence(ImageStats stats) {
        double score = 0.0;

        /*
         * 肤色区域较多，可能有人物。
         * 注意：这只是启发式，不等同于真实人脸识别。
         */
        if (stats.skinRatio > 0.025) {
            score += 0.35;
        }
        if (stats.skinRatio > 0.060) {
            score += 0.25;
        }
        if (stats.skinRatio > 0.120) {
            score += 0.18;
        }

        /*
         * 人像/生活照通常有一定边缘复杂度，但不会像纯文字图那样极端。
         */
        if (stats.edgeDensity > 0.10 && stats.edgeDensity < 0.38) {
            score += 0.12;
        }

        /*
         * 室内人像常出现暖色肤色 + 中等亮度 + 非大面积天空绿色。
         */
        if (stats.skinRatio > 0.04 && stats.greenBlueNatureRatio < 0.45) {
            score += 0.16;
        }

        return clamp(score, 0.0, 1.0);
    }

    /**
     * 粗略判断是否有文字、屏幕、标识、公告牌等信息记录价值。
     */
    private double estimateDocumentaryValue(ImageStats stats) {
        double score = 0.0;

        /*
         * 高频边缘多、局部对比明显，可能包含文字/标识/建筑线条/屏幕信息。
         */
        if (stats.edgeDensity > 0.16) {
            score += 0.25;
        }
        if (stats.edgeDensity > 0.25) {
            score += 0.22;
        }

        /*
         * 水平/垂直线条多，常见于建筑、车站、室内公共空间、牌匾屏幕。
         */
        if (stats.lineLikeRatio > 0.12) {
            score += 0.20;
        }
        if (stats.lineLikeRatio > 0.22) {
            score += 0.16;
        }

        /*
         * 局部高亮矩形/屏幕区域、强对比文字牌，通常有信息记录价值。
         */
        if (stats.highContrastRatio > 0.18) {
            score += 0.18;
        }

        return clamp(score, 0.0, 1.0);
    }

    /**
     * 粗略判断公共场景 / 建筑 / 交通 / 室内空间价值。
     */
    private double estimatePublicSceneValue(ImageStats stats) {
        double score = 0.0;

        /*
         * 大量直线、边缘和中高复杂度，常见于城市建筑、车站、商场、室内空间。
         */
        if (stats.edgeDensity > 0.12 && stats.lineLikeRatio > 0.08) {
            score += 0.28;
        }

        if (stats.edgeDensity > 0.20 && stats.lineLikeRatio > 0.14) {
            score += 0.24;
        }

        /*
         * 画面不是纯自然色，同时结构复杂，可能是公共空间。
         */
        if (stats.greenBlueNatureRatio < 0.40 && stats.edgeDensity > 0.14) {
            score += 0.20;
        }

        /*
         * 暖光/室内光 + 结构边缘，可能是室内公共场景。
         */
        if (stats.warmRatio > 0.20 && stats.edgeDensity > 0.12) {
            score += 0.12;
        }

        return clamp(score, 0.0, 1.0);
    }

    /**
     * 如果画面高度像纯自然风景，社会纪实价值适当降低。
     */
    private double estimatePureNaturePenalty(ImageStats stats) {
        double penalty = 0.0;

        /*
         * 大面积蓝天/水面/绿色植被，且边缘结构不复杂，通常是普通自然风景。
         */
        if (stats.greenBlueNatureRatio > 0.55 && stats.edgeDensity < 0.16) {
            penalty += 0.45;
        }

        if (stats.greenBlueNatureRatio > 0.70 && stats.edgeDensity < 0.22) {
            penalty += 0.30;
        }

        /*
         * 没有人物肤色、没有明显文字/建筑线条时，进一步降低社会价值。
         */
        if (stats.skinRatio < 0.015 && stats.lineLikeRatio < 0.08 && stats.greenBlueNatureRatio > 0.50) {
            penalty += 0.20;
        }

        return clamp(penalty, 0.0, 1.0);
    }

    /**
     * 低质量图像降低记录可用性。
     */
    private double estimateLowQualityPenalty(ImageStats stats) {
        double penalty = 0.0;

        if (stats.brightness < 35 || stats.brightness > 225) {
            penalty += 0.25;
        }

        if (stats.contrast < 18) {
            penalty += 0.25;
        }

        if (stats.edgeDensity < 0.035) {
            penalty += 0.25;
        }

        return clamp(penalty, 0.0, 1.0);
    }

    /**
     * 采样分析图像统计信息。
     */
    private ImageStats analyzeImageStats(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();

        int step = Math.max(1, Math.min(width, height) / 220);

        long count = 0;
        double brightnessSum = 0;
        double brightnessSquareSum = 0;

        int skinCount = 0;
        int greenBlueNatureCount = 0;
        int warmCount = 0;
        int highContrastCount = 0;

        int edgeCount = 0;
        int lineLikeCount = 0;

        for (int y = step; y < height - step; y += step) {
            for (int x = step; x < width - step; x += step) {
                Color c = new Color(image.getRGB(x, y));

                int r = c.getRed();
                int g = c.getGreen();
                int b = c.getBlue();

                double brightness = luminance(r, g, b);

                brightnessSum += brightness;
                brightnessSquareSum += brightness * brightness;
                count++;

                if (isSkinLike(r, g, b)) {
                    skinCount++;
                }

                if (isNatureColor(r, g, b)) {
                    greenBlueNatureCount++;
                }

                if (isWarmColor(r, g, b)) {
                    warmCount++;
                }

                double gx = luminance(image.getRGB(x + step, y)) - luminance(image.getRGB(x - step, y));
                double gy = luminance(image.getRGB(x, y + step)) - luminance(image.getRGB(x, y - step));
                double gradient = Math.sqrt(gx * gx + gy * gy);

                if (gradient > 35) {
                    edgeCount++;
                }

                /*
                 * 水平/垂直边缘明显，近似认为有建筑、文字、屏幕、室内结构。
                 */
                if ((Math.abs(gx) > 45 && Math.abs(gy) < 18)
                        || (Math.abs(gy) > 45 && Math.abs(gx) < 18)) {
                    lineLikeCount++;
                }

                if (gradient > 65) {
                    highContrastCount++;
                }
            }
        }

        if (count == 0) {
            return new ImageStats();
        }

        double avgBrightness = brightnessSum / count;
        double variance = brightnessSquareSum / count - avgBrightness * avgBrightness;
        double contrast = Math.sqrt(Math.max(0, variance));

        ImageStats stats = new ImageStats();
        stats.brightness = avgBrightness;
        stats.contrast = contrast;
        stats.skinRatio = skinCount / (double) count;
        stats.greenBlueNatureRatio = greenBlueNatureCount / (double) count;
        stats.warmRatio = warmCount / (double) count;
        stats.edgeDensity = edgeCount / (double) count;
        stats.lineLikeRatio = lineLikeCount / (double) count;
        stats.highContrastRatio = highContrastCount / (double) count;

        return stats;
    }

    private BufferedImage resizeForAnalysis(BufferedImage source, int maxSide) {
        int width = source.getWidth();
        int height = source.getHeight();

        int longest = Math.max(width, height);
        if (longest <= maxSide) {
            return source;
        }

        double scale = maxSide / (double) longest;
        int newWidth = Math.max(1, (int) Math.round(width * scale));
        int newHeight = Math.max(1, (int) Math.round(height * scale));

        BufferedImage resized = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();

        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(source, 0, 0, newWidth, newHeight, null);
        } finally {
            g.dispose();
        }

        return resized;
    }

    private boolean isSkinLike(int r, int g, int b) {
        /*
         * 简化肤色规则。
         * 兼容暖光/室内人像，但会有误判。
         */
        return r > 80
                && g > 35
                && b > 20
                && r > g
                && g > b
                && (r - b) > 25
                && Math.abs(r - g) > 10;
    }

    private boolean isNatureColor(int r, int g, int b) {
        boolean green = g > r * 1.05 && g > b * 0.90 && g > 70;
        boolean blue = b > r * 1.05 && b > g * 0.90 && b > 80;
        boolean cyanWaterSky = b > 90 && g > 80 && r < 140;
        return green || blue || cyanWaterSky;
    }

    private boolean isWarmColor(int r, int g, int b) {
        return r > 120 && g > 70 && r > b * 1.15;
    }

    private double luminance(int rgb) {
        Color c = new Color(rgb);
        return luminance(c.getRed(), c.getGreen(), c.getBlue());
    }

    private double luminance(int r, int g, int b) {
        return 0.299 * r + 0.587 * g + 0.114 * b;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static class ImageStats {
        double brightness = 128.0;
        double contrast = 40.0;
        double skinRatio = 0.0;
        double greenBlueNatureRatio = 0.0;
        double warmRatio = 0.0;
        double edgeDensity = 0.0;
        double lineLikeRatio = 0.0;
        double highContrastRatio = 0.0;
    }
}