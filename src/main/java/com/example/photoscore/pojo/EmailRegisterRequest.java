package com.example.photoscore.pojo;

import lombok.Data;

@Data
public class EmailRegisterRequest {
    private String username;
    private String password;
    private String email;
    private String emailCode;
    private String captchaId;
    private String captchaCode;
}