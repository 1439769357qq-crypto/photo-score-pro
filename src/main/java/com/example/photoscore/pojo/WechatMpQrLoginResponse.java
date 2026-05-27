package com.example.photoscore.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WechatMpQrLoginResponse {

    private String scene;

    /**
     * 前端用这个 URL 生成二维码。
     */
    private String authUrl;

    private Integer expireSeconds;
}