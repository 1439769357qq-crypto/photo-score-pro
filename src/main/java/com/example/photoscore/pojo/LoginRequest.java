package com.example.photoscore.pojo;

import lombok.Data;

@Data
public class LoginRequest {
    private String account;
    private String password;
    private String captchaId;
    private String captchaCode;
}
