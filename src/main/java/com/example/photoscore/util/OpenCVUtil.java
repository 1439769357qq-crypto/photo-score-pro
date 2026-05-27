package com.example.photoscore.util;

import nu.pattern.OpenCV;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.DataBufferByte;
import java.awt.image.BufferedImage;

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
        BufferedImage bgrImage = toBgrImage(bi);
        byte[] data = ((DataBufferByte) bgrImage.getRaster().getDataBuffer()).getData();
        Mat mat = new Mat(bgrImage.getHeight(), bgrImage.getWidth(), CvType.CV_8UC3);
        mat.put(0, 0, data);
        return mat;
    }

    private static BufferedImage toBgrImage(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_3BYTE_BGR) {
            return source;
        }

        BufferedImage target = new BufferedImage(
                source.getWidth(),
                source.getHeight(),
                BufferedImage.TYPE_3BYTE_BGR
        );

        Graphics2D g = target.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, target.getWidth(), target.getHeight());
            g.drawImage(source, 0, 0, null);
        } finally {
            g.dispose();
        }
        return target;
    }
}
