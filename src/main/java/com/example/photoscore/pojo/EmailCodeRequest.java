package com.example.photoscore.pojo;

import lombok.Data;

@Data
public class EmailCodeRequest {
    private String email;
    private String captchaId;
    private String captchaCode;
}