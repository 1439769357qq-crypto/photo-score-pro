package com.example.photoscore.util;

import org.junit.jupiter.api.Test;
import org.opencv.core.Mat;

import java.awt.Color;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpenCVUtilTest {

    @Test
    void bufferedImageToMatKeepsDimensionsAndBgrChannels() {
        BufferedImage image = new BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, new Color(10, 20, 30).getRGB());

        Mat mat = OpenCVUtil.bufferedImageToMat(image);
        try {
            assertEquals(2, mat.rows());
            assertEquals(3, mat.cols());
            assertEquals(3, mat.channels());

            double[] pixel = mat.get(0, 0);
            assertEquals(30, pixel[0], 0.001);
            assertEquals(20, pixel[1], 0.001);
            assertEquals(10, pixel[2], 0.001);
        } finally {
            mat.release();
        }
    }
}
