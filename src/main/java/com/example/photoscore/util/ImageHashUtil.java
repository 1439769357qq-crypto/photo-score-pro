package com.example.photoscore.util;

import java.io.InputStream;
import java.security.MessageDigest;
import org.springframework.web.multipart.MultipartFile;

public class ImageHashUtil {
    public static String calculateHash(MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int len;
            while ((len = is.read(buffer)) != -1) {
                digest.update(buffer, 0, len);
            }
            byte[] hashBytes = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("计算哈希失败", e);
        }
    }
}