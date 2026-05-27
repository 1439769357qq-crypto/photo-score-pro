package com.example.photoscore.service;

public interface SmsSender {
    void sendLoginCode(String phone, String code);
}
