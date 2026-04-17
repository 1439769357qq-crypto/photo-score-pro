package com.example.photoscore.util;

import nu.pattern.OpenCV;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

public class OpenCVUtil {
    private static boolean initialized = false;
    public static synchronized void init() {
        if (!initialized) {
            OpenCV.loadLocally();
            initialized = true;
        }
    }
    public static Mat bufferedImageToMat(BufferedImage bi) {
        init();
        try (java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream()) {
            ImageIO.write(bi, "jpg", baos);
            byte[] bytes = baos.toByteArray();
            return Imgcodecs.imdecode(new MatOfByte(bytes), Imgcodecs.IMREAD_COLOR);
        } catch (IOException e) {
            throw new RuntimeException("转换失败", e);
        }
    }
}