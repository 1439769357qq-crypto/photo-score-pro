package com.example.photoscore.controller;

import com.example.photoscore.config.WechatLoginProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth/wechat/mp")
public class WechatMpPortalController {

    private final WechatLoginProperties wechatProperties;

    /**
     * 微信公众平台“接口配置信息”URL 验证入口。
     *
     * 微信后台会 GET 请求：
     * /api/auth/wechat/mp/portal?signature=xxx&timestamp=xxx&nonce=xxx&echostr=xxx
     */
    @GetMapping("/portal")
    public String verifyServer(@RequestParam(value = "signature", required = false) String signature,
                               @RequestParam(value = "timestamp", required = false) String timestamp,
                               @RequestParam(value = "nonce", required = false) String nonce,
                               @RequestParam(value = "echostr", required = false) String echostr) {
        if (signature == null || timestamp == null || nonce == null || echostr == null) {
            return "wechat mp portal ok";
        }

        String token = wechatProperties.getVerifyToken();

        if (checkSignature(token, timestamp, nonce, signature)) {
            return echostr;
        }

        return "invalid signature";
    }

    /**
     * 后续如果你要接收公众号消息，可以在这里扩展 POST。
     * 现在先保留，避免微信推送消息时报 405。
     */
    @PostMapping("/portal")
    public String receiveMessage(@RequestBody(required = false) String body) {
        return "success";
    }

    private boolean checkSignature(String token, String timestamp, String nonce, String signature) {
        try {
            String[] arr = new String[]{token, timestamp, nonce};
            Arrays.sort(arr);

            String raw = arr[0] + arr[1] + arr[2];
            String sha1 = sha1(raw);

            return sha1.equalsIgnoreCase(signature);
        } catch (Exception e) {
            return false;
        }
    }

    private String sha1(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));

        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(b & 0xff);
            if (hex.length() == 1) {
                sb.append('0');
            }
            sb.append(hex);
        }

        return sb.toString();
    }
}