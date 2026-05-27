package com.example.photoscore.pojo;

import lombok.Data;

@Data
public class RegisterRequest {
    private String username;
    private String password;
    private String phone;
    private String smsCode;
    private String captchaId;
    private String captchaCode;
}
