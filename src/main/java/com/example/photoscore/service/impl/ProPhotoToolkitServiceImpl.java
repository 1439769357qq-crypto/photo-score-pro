package com.example.photoscore.service.impl;

import com.example.photoscore.dto.ProPhotoToolkitDtos;
import com.example.photoscore.service.ProPhotoToolkitService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProPhotoToolkitServiceImpl implements ProPhotoToolkitService {

    private static final int ANALYSIS_MAX_SIDE = 520;
    private static final int MAX_BATCH_SIZE = 40;

    private static final String GROUP_EXCELLENT = "Recommended delivery";
    private static final String GROUP_USABLE = "Usable backup";
    private static final String GROUP_RETOUCH = "Retouch first";
    private static final String GROUP_ELIMINATE = "Cull";

    @Override
    public ProPhotoToolkitDtos.SingleReport analyzeSingle(MultipartFile file) {
        validateFile(file);
        AnalyzedImage analyzed = analyzeImage(file, 1);
        return buildSingleReport(analyzed);
    }

    @Override
    public ProPhotoToolkitDtos.BatchCullResponse analyzeBatch(MultipartFile[] files) {
        if (files == null || files.length == 0) {
            throw new IllegalArgumentException("Please upload at least one photo.");
        }
        if (files.length > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("A single batch can analyze at most " + MAX_BATCH_SIZE + " photos.");
        }

        List<AnalyzedImage> analyzedList = new ArrayList<>();

        int index = 1;
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            analyzedList.add(analyzeImage(file, index++));
        }

        if (analyzedList.isEmpty()) {
            throw new IllegalArgumentException("No valid image files were found.");
        }

        analyzedList.sort(Comparator.comparingDouble((AnalyzedImage a) -> a.technicalScore).reversed());

        List<ProPhotoToolkitDtos.BatchItem> items = new ArrayList<>();
        int rank = 1;

        int excellent = 0;
        int usable = 0;
        int retouch = 0;
        int eliminate = 0;

        for (AnalyzedImage a : analyzedList) {
            String group = groupByScore(a.technicalScore);

            switch (group) {
                case GROUP_EXCELLENT -> excellent++;
                case GROUP_USABLE -> usable++;
                case GROUP_RETOUCH -> retouch++;
                default -> eliminate++;
            }

            items.add(ProPhotoToolkitDtos.BatchItem.builder()
                    .rank(rank++)
                    .fileName(a.fileName)
                    .width(a.width)
                    .height(a.height)
                    .dimension(a.width + " x " + a.height)
                    .technicalScore(round1(a.technicalScore))
                    .grade(grade(a.technicalScore))
                    .group(group)
                    .summary(buildBatchSummary(a))
                    .risks(buildRisks(a))
                    .keepAdvice(buildKeepAdvice(a))
                    .build());
        }

        List<ProPhotoToolkitDtos.DuplicatePair> duplicates = detectDuplicates(analyzedList);

        return ProPhotoToolkitDtos.BatchCullResponse.builder()
                .totalCount(files.length)
                .analyzedCount(analyzedList.size())
                .excellentCount(excellent)
                .usableCount(usable)
                .retouchCount(retouch)
                .eliminateCount(eliminate)
                .batchConclusion(buildBatchConclusion(analyzedList, duplicates))
                .items(items)
                .duplicatePairs(duplicates)
                .deliveryAdvice(buildDeliveryAdvice(excellent, usable, retouch, eliminate, duplicates))
                .build();
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Please upload one photo.");
        }
    }

    private AnalyzedImage analyzeImage(MultipartFile file, int index) {
        String fileName = safeFileName(file.getOriginalFilename(), "photo_" + index);

        try (InputStream input = file.getInputStream()) {
            BufferedImage source = ImageIO.read(input);

            if (source == null) {
                throw new IllegalArgumentException("Unable to read image file: " + fileName);
            }

            BufferedImage img = resizeForAnalysis(source, ANALYSIS_MAX_SIDE);

            int w = img.getWidth();
            int h = img.getHeight();
            int total = w * h;
            int[] pixels = img.getRGB(0, 0, w, h, null, 0, w);
            double[] gray = new double[total];

            double sumLum = 0;
            double sumLum2 = 0;
            double sumSat = 0;
            double sumR = 0;
            double sumG = 0;
            double sumB = 0;

            int[] hist = new int[256];

            for (int i = 0; i < pixels.length; i++) {
                int rgb = pixels[i];
                int r = (rgb >> 16) & 0xff;
                int g = (rgb >> 8) & 0xff;
                int b = rgb & 0xff;

                double lum = 0.2126 * r + 0.7152 * g + 0.0722 * b;
                gray[i] = lum;

                sumLum += lum;
                sumLum2 += lum * lum;
                sumR += r;
                sumG += g;
                sumB += b;

                int max = Math.max(r, Math.max(g, b));
                int min = Math.min(r, Math.min(g, b));
                double sat = max == 0 ? 0 : (max - min) * 1.0 / max;
                sumSat += sat;

                hist[(int) Math.round(lum)]++;
            }

            double meanLum = sumLum / total;
            double variance = Math.max(0, sumLum2 / total - meanLum * meanLum);
            double std = Math.sqrt(variance);

            double brightness = meanLum / 255.0 * 100.0;
            double contrast = clamp(std / 80.0 * 100.0, 0, 100);
            double saturation = clamp(sumSat / total * 100.0, 0, 100);

            int p5 = percentile(hist, total, 0.05);
            int p95 = percentile(hist, total, 0.95);
            double dynamicRange = clamp((p95 - p5) / 255.0 * 100.0, 0, 100);

            double sharpness = calcSharpness(gray, w, h);
            double noiseControl = calcNoiseControl(gray, w, h);

            double meanR = sumR / total;
            double meanG = sumG / total;
            double meanB = sumB / total;
            double colorCast = calcColorCast(meanR, meanG, meanB);
            String colorTemperature = colorTemperature(meanR, meanG, meanB);
            String toneStyle = toneStyle(brightness, contrast, saturation);

            double exposureQuality = clamp(100.0 - Math.abs(brightness - 52.0) * 2.2, 0, 100);

            double score =
                    sharpness * 0.24
                            + exposureQuality * 0.20
                            + contrast * 0.14
                            + dynamicRange * 0.14
                            + noiseControl * 0.16
                            + saturation * 0.06
                            + (100 - colorCast) * 0.06;

            long hash = averageHash(img);

            AnalyzedImage a = new AnalyzedImage();
            a.index = index;
            a.fileName = fileName;
            a.fileSize = file.getSize();
            a.width = source.getWidth();
            a.height = source.getHeight();
            a.megapixels = (double) source.getWidth() * source.getHeight() / 1_000_000.0;
            a.orientation = source.getWidth() >= source.getHeight() ? "Landscape" : "Portrait";
            a.aspectRatio = ratio(source.getWidth(), source.getHeight());

            a.brightness = brightness;
            a.exposureQuality = exposureQuality;
            a.contrast = contrast;
            a.dynamicRange = dynamicRange;
            a.sharpness = sharpness;
            a.noiseControl = noiseControl;
            a.saturation = saturation;
            a.colorCast = colorCast;
            a.colorTemperature = colorTemperature;
            a.toneStyle = toneStyle;
            a.technicalScore = clamp(score, 0, 100);
            a.hash = hash;

            return a;
        } catch (IOException e) {
            throw new RuntimeException("Photo analysis failed: " + e.getMessage(), e);
        }
    }

    private ProPhotoToolkitDtos.SingleReport buildSingleReport(AnalyzedImage a) {
        List<String> risks = buildRisks(a);
        List<String> strengths = buildStrengths(a);

        return ProPhotoToolkitDtos.SingleReport.builder()
                .fileName(a.fileName)
                .fileSize(a.fileSize)
                .width(a.width)
                .height(a.height)
                .dimension(a.width + " x " + a.height)
                .megapixels(round2(a.megapixels))
                .orientation(a.orientation)
                .aspectRatio(a.aspectRatio)
                .technicalScore(round1(a.technicalScore))
                .grade(grade(a.technicalScore))
                .professionalConclusion(buildProfessionalConclusion(a, risks, strengths))
                .metrics(ProPhotoToolkitDtos.ImageMetrics.builder()
                        .brightness(round1(a.brightness))
                        .exposureQuality(round1(a.exposureQuality))
                        .contrast(round1(a.contrast))
                        .dynamicRange(round1(a.dynamicRange))
                        .sharpness(round1(a.sharpness))
                        .noiseControl(round1(a.noiseControl))
                        .saturation(round1(a.saturation))
                        .colorCast(round1(a.colorCast))
                        .colorTemperature(a.colorTemperature)
                        .toneStyle(a.toneStyle)
                        .build())
                .strengths(strengths)
                .risks(risks)
                .useAdvice(buildUseAdvice(a))
                .retouchPreset(buildRetouchPreset(a))
                .build();
    }

    private List<ProPhotoToolkitDtos.DuplicatePair> detectDuplicates(List<AnalyzedImage> images) {
        List<ProPhotoToolkitDtos.DuplicatePair> pairs = new ArrayList<>();

        for (int i = 0; i < images.size(); i++) {
            for (int j = i + 1; j < images.size(); j++) {
                AnalyzedImage a = images.get(i);
                AnalyzedImage b = images.get(j);

                int dist = Long.bitCount(a.hash ^ b.hash);

                if (dist <= 7) {
                    double similarity = (64 - dist) / 64.0 * 100.0;

                    pairs.add(ProPhotoToolkitDtos.DuplicatePair.builder()
                            .fileA(a.fileName)
                            .fileB(b.fileName)
                            .hammingDistance(dist)
                            .similarity(round1(similarity))
                            .reason(dist <= 3
                                    ? "Very similar; keep the higher scoring frame."
                                    : "Similar structure; treat as an alternate from the same set.")
                            .build());
                }
            }
        }

        pairs.sort(Comparator.comparingInt(ProPhotoToolkitDtos.DuplicatePair::getHammingDistance));
        return pairs;
    }

    private ProPhotoToolkitDtos.RetouchPreset buildRetouchPreset(AnalyzedImage a) {
        Map<String, String> params = new LinkedHashMap<>();

        String exposure = a.brightness < 42 ? "+0.30EV to +0.70EV" : a.brightness > 68 ? "-0.20EV to -0.50EV" : "Keep or fine tune";
        String contrast = a.contrast < 38 ? "+10 to +18" : a.contrast > 78 ? "-6 to -12" : "+3 to +8";
        String highlights = a.brightness > 66 ? "-20 to -35" : "-5 to -15";
        String shadows = a.brightness < 42 ? "+15 to +30" : "+5 to +12";
        String clarity = a.sharpness < 45 ? "+8 to +15" : "+3 to +8";
        String texture = a.sharpness < 45 ? "+10 to +18" : "+4 to +10";
        String dehaze = a.contrast < 35 ? "+8 to +16" : "+2 to +6";
        String vibrance = a.saturation < 35 ? "+12 to +22" : a.saturation > 72 ? "-5 to -10" : "+4 to +10";
        String noiseReduction = a.noiseControl < 50 ? "+20 to +35" : "+5 to +12";
        String sharpen = a.sharpness < 45 ? "+25 to +40" : "+10 to +20";
        String whiteBalance = switch (a.colorTemperature) {
            case "Warm" -> "Temperature -300K to -700K";
            case "Cool" -> "Temperature +300K to +700K";
            case "Green" -> "Tint +5 to +12";
            case "Magenta" -> "Tint -5 to -12";
            default -> "Keep current white balance";
        };

        params.put("Exposure", exposure);
        params.put("Contrast", contrast);
        params.put("Highlights", highlights);
        params.put("Shadows", shadows);
        params.put("Clarity", clarity);
        params.put("Texture", texture);
        params.put("Dehaze", dehaze);
        params.put("Vibrance", vibrance);
        params.put("Noise reduction", noiseReduction);
        params.put("Sharpen", sharpen);
        params.put("White balance", whiteBalance);

        return ProPhotoToolkitDtos.RetouchPreset.builder()
                .exposure(exposure)
                .contrast(contrast)
                .highlights(highlights)
                .shadows(shadows)
                .clarity(clarity)
                .texture(texture)
                .dehaze(dehaze)
                .vibrance(vibrance)
                .noiseReduction(noiseReduction)
                .sharpen(sharpen)
                .whiteBalance(whiteBalance)
                .summary("Correct exposure and white balance first, then refine detail, noise, and local contrast.")
                .parameters(params)
                .build();
    }

    private List<String> buildStrengths(AnalyzedImage a) {
        List<String> list = new ArrayList<>();

        if (a.exposureQuality >= 75) {
            list.add("Stable exposure with a balanced brightness distribution.");
        }
        if (a.sharpness >= 68) {
            list.add("Good sharpness and useful subject detail.");
        }
        if (a.noiseControl >= 70) {
            list.add("Clean image with controlled visible noise.");
        }
        if (a.dynamicRange >= 62) {
            list.add("Good tonal range with room for post-processing.");
        }
        if (a.saturation >= 38 && a.saturation <= 68) {
            list.add("Natural saturation suitable for further retouching.");
        }

        if (list.isEmpty()) {
            list.add("The photo has baseline documentary value and may improve with editing.");
        }

        return list;
    }

    private List<String> buildRisks(AnalyzedImage a) {
        List<String> list = new ArrayList<>();

        if (a.brightness < 35) {
            list.add("Underexposure risk: shadow detail may be insufficient.");
        }
        if (a.brightness > 72) {
            list.add("Overexposure risk: highlight detail may be clipped.");
        }
        if (a.sharpness < 45) {
            list.add("Sharpness risk: fine detail may look soft when enlarged.");
        }
        if (a.noiseControl < 50) {
            list.add("Noise risk: apply denoise before final delivery.");
        }
        if (a.dynamicRange < 38) {
            list.add("Tonal risk: low dynamic range may look flat.");
        }
        if (a.colorCast > 28) {
            list.add("Color cast risk: correct white balance or tint first.");
        }
        if (a.megapixels < 2.0) {
            list.add("Resolution risk: avoid large-size output.");
        }

        if (list.isEmpty()) {
            list.add("No obvious technical risk detected.");
        }

        return list;
    }

    private List<String> buildUseAdvice(AnalyzedImage a) {
        List<String> list = new ArrayList<>();

        if (a.technicalScore >= 85) {
            list.add("Suitable for portfolio display, client delivery, social covers, and HD screen use.");
            list.add("Run manual color and detail checks before large commercial print output.");
        } else if (a.technicalScore >= 72) {
            list.add("Suitable for social publishing, event records, article images, and general commercial material.");
            list.add("Apply basic exposure, sharpening, denoise, and crop adjustments before publishing.");
        } else if (a.technicalScore >= 60) {
            list.add("Suitable for internal records, backup material, and non-critical content.");
            list.add("Avoid using it directly as a hero image, poster, large print, or premium delivery.");
        } else {
            list.add("Suitable only for records, review, or low-requirement usage.");
            list.add("Not recommended for formal delivery, commercial promotion, portfolio display, or large output.");
        }

        return list;
    }

    private String buildProfessionalConclusion(AnalyzedImage a, List<String> risks, List<String> strengths) {
        return "The system evaluated exposure, sharpness, dynamic range, noise control, color tendency, and resolution. "
                + "This photo scored " + round1(a.technicalScore) + " with grade " + grade(a.technicalScore) + ". "
                + "Main strengths: " + strengths.stream().limit(2).collect(Collectors.joining("; ")) + ". "
                + "Main risks: " + risks.stream().limit(2).collect(Collectors.joining("; ")) + ". "
                + "Use the retouch preset as a starting point before final delivery.";
    }

    private String buildBatchSummary(AnalyzedImage a) {
        return "Score " + round1(a.technicalScore) + ", " + a.toneStyle
                + ", sharpness " + round1(a.sharpness)
                + ", exposure quality " + round1(a.exposureQuality)
                + ", noise control " + round1(a.noiseControl) + ".";
    }

    private String buildKeepAdvice(AnalyzedImage a) {
        if (a.technicalScore >= 85) {
            return "Keep first; ready as a delivery candidate.";
        }
        if (a.technicalScore >= 72) {
            return "Keep; use after light retouching.";
        }
        if (a.technicalScore >= 60) {
            return "Keep as a backup and retouch before use.";
        }
        return "Cull unless it has special documentary value.";
    }

    private String buildBatchConclusion(List<AnalyzedImage> list, List<ProPhotoToolkitDtos.DuplicatePair> duplicates) {
        double avg = list.stream().mapToDouble(a -> a.technicalScore).average().orElse(0);
        long high = list.stream().filter(a -> a.technicalScore >= 72).count();

        return "Analyzed " + list.size() + " photos with an average technical score of " + round1(avg)
                + ". " + high + " photos are strong keep candidates. "
                + "Detected " + duplicates.size() + " similar pairs; combine score and purpose when culling duplicates.";
    }

    private List<String> buildDeliveryAdvice(int excellent, int usable, int retouch, int eliminate,
                                             List<ProPhotoToolkitDtos.DuplicatePair> duplicates) {
        List<String> list = new ArrayList<>();

        list.add("Delivery split: " + excellent + " recommended, " + usable + " backups, "
                + retouch + " need retouch, " + eliminate + " suggested culls.");
        list.add("Prioritize the recommended group for final delivery and use backups to complete the story.");
        list.add("Retouch exposure, color, detail, and noise before using photos in the retouch group.");
        list.add("Cull low-scoring photos unless they carry unique documentary value.");

        if (!duplicates.isEmpty()) {
            list.add("Similar photos were detected; keep the sharper and higher-scoring frame in each pair.");
        }

        return list;
    }

    private String groupByScore(double score) {
        if (score >= 85) {
            return GROUP_EXCELLENT;
        }
        if (score >= 72) {
            return GROUP_USABLE;
        }
        if (score >= 60) {
            return GROUP_RETOUCH;
        }
        return GROUP_ELIMINATE;
    }

    private String grade(double score) {
        if (score >= 90) {
            return "S";
        }
        if (score >= 80) {
            return "A";
        }
        if (score >= 70) {
            return "B";
        }
        if (score >= 60) {
            return "C";
        }
        return "D";
    }

    private BufferedImage resizeForAnalysis(BufferedImage src, int maxSide) {
        int w = src.getWidth();
        int h = src.getHeight();
        int max = Math.max(w, h);

        if (max <= maxSide && src.getType() == BufferedImage.TYPE_INT_RGB) {
            return src;
        }

        double scale = max <= maxSide ? 1.0 : maxSide * 1.0 / max;
        int nw = Math.max(1, (int) Math.round(w * scale));
        int nh = Math.max(1, (int) Math.round(h * scale));

        BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
        g.drawImage(src, 0, 0, nw, nh, null);
        g.dispose();

        return out;
    }

    private int percentile(int[] hist, int total, double p) {
        int target = Math.max(1, (int) Math.ceil(total * p));
        int count = 0;

        for (int i = 0; i < hist.length; i++) {
            count += hist[i];
            if (count >= target) {
                return i;
            }
        }

        return 255;
    }

    private double calcSharpness(double[] gray, int w, int h) {
        if (w < 3 || h < 3) {
            return 0;
        }

        double sum = 0;
        int count = 0;

        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                double c = gray[y * w + x];
                double lap = -4 * c
                        + gray[y * w + x - 1]
                        + gray[y * w + x + 1]
                        + gray[(y - 1) * w + x]
                        + gray[(y + 1) * w + x];

                sum += Math.abs(lap);
                count++;
            }
        }

        double avg = sum / Math.max(1, count);
        return clamp(Math.log10(avg + 1) * 42.0, 0, 100);
    }

    private double calcNoiseControl(double[] gray, int w, int h) {
        if (w < 3 || h < 3) {
            return 70;
        }

        double sum = 0;
        int count = 0;

        for (int y = 1; y < h - 1; y += 2) {
            for (int x = 1; x < w - 1; x += 2) {
                double c = gray[y * w + x];
                double avg = (
                        gray[y * w + x - 1]
                                + gray[y * w + x + 1]
                                + gray[(y - 1) * w + x]
                                + gray[(y + 1) * w + x]
                ) / 4.0;

                sum += Math.abs(c - avg);
                count++;
            }
        }

        double noise = sum / Math.max(1, count);
        return clamp(100.0 - noise * 3.2, 0, 100);
    }

    private double calcColorCast(double r, double g, double b) {
        double avg = (r + g + b) / 3.0;
        double maxDiff = Math.max(Math.abs(r - avg), Math.max(Math.abs(g - avg), Math.abs(b - avg)));
        return clamp(maxDiff / 60.0 * 100.0, 0, 100);
    }

    private String colorTemperature(double r, double g, double b) {
        if (r - b > 16) {
            return "Warm";
        }
        if (b - r > 16) {
            return "Cool";
        }
        if (g - Math.max(r, b) > 12) {
            return "Green";
        }
        if (Math.min(r, b) - g > 10) {
            return "Magenta";
        }
        return "Neutral";
    }

    private String toneStyle(double brightness, double contrast, double saturation) {
        if (brightness < 38) {
            return "low-key dark style";
        }
        if (brightness > 70) {
            return "high-key bright style";
        }
        if (contrast < 35) {
            return "soft low-contrast style";
        }
        if (contrast > 72) {
            return "strong contrast style";
        }
        if (saturation < 30) {
            return "muted low-saturation style";
        }
        if (saturation > 72) {
            return "vivid high-saturation style";
        }
        return "natural balanced style";
    }

    private long averageHash(BufferedImage img) {
        BufferedImage small = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = small.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(img, 0, 0, 8, 8, null);
        g.dispose();

        int[] pixels = small.getRGB(0, 0, 8, 8, null, 0, 8);
        int[] vals = new int[64];
        int sum = 0;

        for (int i = 0; i < pixels.length; i++) {
            int rgb = pixels[i];
            int r = (rgb >> 16) & 0xff;
            int g2 = (rgb >> 8) & 0xff;
            int b = rgb & 0xff;
            int gray = (int) Math.round(0.299 * r + 0.587 * g2 + 0.114 * b);
            vals[i] = gray;
            sum += gray;
        }

        int avg = sum / 64;
        long hash = 0;

        for (int i = 0; i < 64; i++) {
            if (vals[i] >= avg) {
                hash |= (1L << i);
            }
        }

        return hash;
    }

    private String ratio(int w, int h) {
        int gcd = gcd(w, h);
        return (w / gcd) + ":" + (h / gcd);
    }

    private int gcd(int a, int b) {
        while (b != 0) {
            int t = a % b;
            a = b;
            b = t;
        }
        return Math.max(1, a);
    }

    private String safeFileName(String name, String fallback) {
        return name == null || name.isBlank() ? fallback : name;
    }

    private double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static class AnalyzedImage {
        int index;
        String fileName;
        long fileSize;
        int width;
        int height;
        double megapixels;
        String orientation;
        String aspectRatio;

        double brightness;
        double exposureQuality;
        double contrast;
        double dynamicRange;
        double sharpness;
        double noiseControl;
        double saturation;
        double colorCast;
        String colorTemperature;
        String toneStyle;

        double technicalScore;
        long hash;
    }
}
