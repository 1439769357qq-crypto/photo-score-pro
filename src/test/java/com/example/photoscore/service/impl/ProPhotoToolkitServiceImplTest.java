package com.example.photoscore.service.impl;

import com.example.photoscore.dto.ProPhotoToolkitDtos;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProPhotoToolkitServiceImplTest {

    private final ProPhotoToolkitServiceImpl service = new ProPhotoToolkitServiceImpl();

    @Test
    void analyzeSingleReturnsMetricsForValidImage() throws Exception {
        ProPhotoToolkitDtos.SingleReport report = service.analyzeSingle(imageFile("single.jpg", 160, 120));

        assertEquals("single.jpg", report.getFileName());
        assertEquals(160, report.getWidth());
        assertEquals(120, report.getHeight());
        assertNotNull(report.getMetrics());
        assertNotNull(report.getTechnicalScore());
        assertFalse(report.getRisks().isEmpty());
        assertFalse(report.getStrengths().isEmpty());
    }

    @Test
    void analyzeBatchRanksImagesAndDetectsDuplicates() throws Exception {
        MockMultipartFile first = imageFile("first.jpg", 96, 96);
        MockMultipartFile duplicate = imageFile("duplicate.jpg", 96, 96);

        ProPhotoToolkitDtos.BatchCullResponse response = service.analyzeBatch(new MockMultipartFile[]{first, duplicate});

        assertEquals(2, response.getTotalCount());
        assertEquals(2, response.getAnalyzedCount());
        assertEquals(2, response.getItems().size());
        assertTrue(response.getDuplicatePairs().size() >= 1);
    }

    private MockMultipartFile imageFile(String name, int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(32, 80, 140));
        g.fillRect(0, 0, width, height);
        g.setColor(new Color(220, 200, 120));
        g.fillOval(width / 5, height / 5, width / 2, height / 2);
        g.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);

        return new MockMultipartFile(name, name, "image/jpeg", out.toByteArray());
    }
}
