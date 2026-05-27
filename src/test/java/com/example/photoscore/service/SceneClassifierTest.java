package com.example.photoscore.service;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SceneClassifierTest {

    private final SceneClassifier classifier = new SceneClassifier();

    @Test
    void classifiesPersonInOutdoorSceneAsPortrait() {
        BufferedImage image = outdoorBackground();
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(38, 52, 76));
            g.fillRoundRect(112, 215, 76, 138, 24, 24);
            g.setColor(new Color(228, 172, 132));
            g.fillOval(111, 112, 78, 92);
            g.fillRoundRect(137, 195, 26, 34, 12, 12);
            g.fillOval(87, 245, 28, 36);
            g.fillOval(186, 245, 28, 36);
            g.setColor(new Color(58, 39, 28));
            g.fillArc(108, 98, 84, 68, 0, 180);
        } finally {
            g.dispose();
        }

        assertEquals("人物特写", classifier.classify(image));
    }

    @Test
    void keepsPlainOutdoorSceneAsLandscape() {
        assertEquals("自然风景", classifier.classify(outdoorBackground()));
    }

    private BufferedImage outdoorBackground() {
        BufferedImage image = new BufferedImage(300, 420, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(new Color(102, 176, 238));
            g.fillRect(0, 0, image.getWidth(), 190);
            g.setColor(new Color(58, 136, 72));
            g.fillRect(0, 190, image.getWidth(), image.getHeight() - 190);
            g.setColor(new Color(238, 240, 228));
            g.fillOval(28, 40, 86, 24);
            g.fillOval(182, 70, 92, 26);
        } finally {
            g.dispose();
        }
        return image;
    }
}
