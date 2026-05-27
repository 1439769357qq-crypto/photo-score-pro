package com.example.photoscore.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WechatLoginUrlResponse {
    private String loginUrl;
    private String state;
}