package com.example.photoscore.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Data
@Component
@Profile("render")
@ConfigurationProperties(prefix = "render-sms")
public class RenderGuoyangSmsProperties {

    private boolean enabled = true;

    /**
     * guoyang / console / fixed
     */
    private String provider = "guoyang";

    private String fixedCode = "123456";

    private int codeExpireMinutes = 5;

    private int resendIntervalSeconds = 60;

    private int requestTimeoutSeconds = 15;

    private Guoyang guoyang = new Guoyang();

    @Data
    public static class Guoyang {

        /**
         * https://gyytz.market.alicloudapi.com/sms/smsSend
         */
        private String url = "https://gyytz.market.alicloudapi.com/sms/smsSend";

        /**
         * 阿里云市场 AppCode。
         * Header: Authorization: APPCODE xxxx
         */
        private String appCode;

        /**
         * 短信签名 ID。
         */
        private String smsSignId;

        /**
         * 短信模板 ID。
         */
        private String templateId;

        /**
         * 验证码有效分钟数，对应 param 里的 **minute**。
         */
        private int minute = 5;
    }
}