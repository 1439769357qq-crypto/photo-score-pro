package com.example.photoscore.pojo;

import lombok.Data;

@Data
public class SmsCodeRequest {
    private String phone;
    private String captchaId;
    private String captchaCode;
}
