package com.example.photoscore.pojo;

import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

public class CachedMultipartFile implements MultipartFile {

    private final String name;
    private final String originalFilename;
    private final String contentType;
    private final byte[] bytes;

    public CachedMultipartFile(MultipartFile file) throws IOException {
        this.name = file.getName();
        this.originalFilename = file.getOriginalFilename();
        this.contentType = file.getContentType();
        this.bytes = file.getBytes(); // 关键：必须在 Controller 请求线程内读取
    }

    public CachedMultipartFile(String name, String originalFilename, String contentType, byte[] bytes) {
        this.name = name;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.bytes = bytes == null ? new byte[0] : bytes;
    }

    @Override
    public String getName() {
        return name == null ? "files" : name;
    }

    @Override
    public String getOriginalFilename() {
        return originalFilename;
    }

    @Override
    public String getContentType() {
        return contentType == null ? "application/octet-stream" : contentType;
    }

    @Override
    public boolean isEmpty() {
        return bytes.length == 0;
    }

    @Override
    public long getSize() {
        return bytes.length;
    }

    @Override
    public byte[] getBytes() {
        return bytes;
    }

    @Override
    public InputStream getInputStream() {
        return new ByteArrayInputStream(bytes);
    }

    @Override
    public void transferTo(File dest) throws IOException {
        Files.write(dest.toPath(), bytes);
    }
}