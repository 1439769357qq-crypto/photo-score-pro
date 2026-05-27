package com.example.photoscore.pojo;

import lombok.Data;

@Data
public class SmsLoginRequest {
    private String phone;
    private String smsCode;
}
