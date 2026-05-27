package com.example.photoscore.service;

import com.example.photoscore.pojo.*;

public interface AuthService {

    CaptchaResponse createCaptcha();

    void sendSmsCode(SmsCodeRequest request, String ip);

    void sendEmailCode(EmailCodeRequest request, String ip);

    AuthResponse register(RegisterRequest request);

    AuthResponse emailRegister(EmailRegisterRequest request);

    AuthResponse login(LoginRequest request);

    AuthResponse smsLogin(SmsLoginRequest request);

    UserInfoResponse getUserInfo(Long userId);

    /**
     * 微信公众平台：创建电脑端扫码登录二维码。
     */
    WechatMpQrLoginResponse createWechatMpQrLogin();

    /**
     * 微信公众平台：电脑端轮询扫码结果。
     */
    WechatMpPollResponse pollWechatMpLogin(String scene);

    /**
     * 微信公众平台：微信授权回调。
     */
    String handleWechatMpCallback(String code, String state);
}