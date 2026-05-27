package com.example.photoscore.util;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.util.Base64;

public class CaptchaUtil {

    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private CaptchaUtil() {}

    public static CaptchaImage generate() {
        int width = 130;
        int height = 46;

        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            code.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();

        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(245, 247, 250));
            g.fillRect(0, 0, width, height);

            for (int i = 0; i < 10; i++) {
                g.setColor(randomColor(150, 230));
                int x1 = RANDOM.nextInt(width);
                int y1 = RANDOM.nextInt(height);
                int x2 = RANDOM.nextInt(width);
                int y2 = RANDOM.nextInt(height);
                g.drawLine(x1, y1, x2, y2);
            }

            g.setFont(new Font("Arial", Font.BOLD, 28));

            for (int i = 0; i < code.length(); i++) {
                g.setColor(randomColor(40, 120));
                int x = 18 + i * 26;
                int y = 31 + RANDOM.nextInt(6);
                double theta = (RANDOM.nextDouble() - 0.5) * 0.55;
                g.rotate(theta, x, y);
                g.drawString(String.valueOf(code.charAt(i)), x, y);
                g.rotate(-theta, x, y);
            }

            for (int i = 0; i < 45; i++) {
                g.setColor(randomColor(120, 240));
                g.fillOval(RANDOM.nextInt(width), RANDOM.nextInt(height), 2, 2);
            }
        } finally {
            g.dispose();
        }

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            String base64 = "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
            return new CaptchaImage(code.toString(), base64);
        } catch (Exception e) {
            throw new RuntimeException("生成图形验证码失败", e);
        }
    }

    private static Color randomColor(int min, int max) {
        int r = min + RANDOM.nextInt(Math.max(1, max - min));
        int g = min + RANDOM.nextInt(Math.max(1, max - min));
        int b = min + RANDOM.nextInt(Math.max(1, max - min));
        return new Color(r, g, b);
    }

    public record CaptchaImage(String code, String imageBase64) {}
}
