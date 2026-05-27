package com.example.photoscore.service;

public interface SmsAuthService {

    /**
     * 发送验证码。
     */
    void sendCode(String phone, String ip);

    /**
     * 校验验证码。
     */
    void verifyCode(String phone, String code);
}