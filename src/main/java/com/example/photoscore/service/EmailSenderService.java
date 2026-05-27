package com.example.photoscore.service;

public interface EmailSenderService {
    void sendRegisterCode(String email, String code);
}