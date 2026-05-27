package com.example.photoscore.pojo;

import com.example.photoscore.util.OpenCVUtil;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.awt.image.BufferedImage;

public class ScoringImageContext implements AutoCloseable {

    private final BufferedImage image;
    private Mat bgr;
    private Mat gray;
    private Mat hsv;
    private Mat edges50To150;
    private boolean closed;

    private ScoringImageContext(BufferedImage image) {
        if (image == null) {
            throw new IllegalArgumentException("image must not be null");
        }
        this.image = image;
    }

    public static ScoringImageContext from(BufferedImage image) {
        return new ScoringImageContext(image);
    }

    public BufferedImage getImage() {
        ensureOpen();
        return image;
    }

    public Mat getBgr() {
        ensureOpen();
        if (bgr == null) {
            bgr = OpenCVUtil.bufferedImageToMat(image);
        }
        return bgr;
    }

    public Mat getGray() {
        ensureOpen();
        if (gray == null) {
            gray = new Mat();
            Imgproc.cvtColor(getBgr(), gray, Imgproc.COLOR_BGR2GRAY);
        }
        return gray;
    }

    public Mat getHsv() {
        ensureOpen();
        if (hsv == null) {
            hsv = new Mat();
            Imgproc.cvtColor(getBgr(), hsv, Imgproc.COLOR_BGR2HSV);
        }
        return hsv;
    }

    public Mat getEdges50To150() {
        ensureOpen();
        if (edges50To150 == null) {
            edges50To150 = new Mat();
            Imgproc.Canny(getGray(), edges50To150, 50, 150);
        }
        return edges50To150;
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("ScoringImageContext has been closed");
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        BaseScorer.safeRelease(edges50To150, hsv, gray, bgr);
        edges50To150 = null;
        hsv = null;
        gray = null;
        bgr = null;
        closed = true;
    }
}
