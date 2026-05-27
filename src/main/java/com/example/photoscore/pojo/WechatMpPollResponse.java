package com.example.photoscore.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WechatMpPollResponse {

    /**
     * PENDING / CONFIRMED / EXPIRED / ERROR
     */
    private String status;

    private String message;

    private AuthResponse auth;
}