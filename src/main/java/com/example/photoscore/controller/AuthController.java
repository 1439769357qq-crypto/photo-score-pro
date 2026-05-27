package com.example.photoscore.controller;

import com.example.photoscore.config.WechatLoginProperties;
import com.example.photoscore.pojo.*;
import com.example.photoscore.security.JwtUtil;
import com.example.photoscore.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtUtil jwtUtil;
    private final WechatLoginProperties wechatProperties;

    @GetMapping("/captcha")
    public ApiResponse<CaptchaResponse> captcha() {
        return ApiResponse.success(authService.createCaptcha());
    }

    @PostMapping("/sms-code")
    public ApiResponse<Void> smsCode(@RequestBody SmsCodeRequest request,
                                     HttpServletRequest servletRequest) {
        authService.sendSmsCode(request, clientIp(servletRequest));
        return ApiResponse.success(null);
    }

    @PostMapping("/email-code")
    public ApiResponse<Void> emailCode(@RequestBody EmailCodeRequest request,
                                       HttpServletRequest servletRequest) {
        authService.sendEmailCode(request, clientIp(servletRequest));
        return ApiResponse.success(null);
    }

    @PostMapping("/register")
    public ApiResponse<AuthResponse> register(@RequestBody RegisterRequest request) {
        return ApiResponse.success(authService.register(request));
    }

    @PostMapping("/email-register")
    public ApiResponse<AuthResponse> emailRegister(@RequestBody EmailRegisterRequest request) {
        return ApiResponse.success(authService.emailRegister(request));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @PostMapping("/sms-login")
    public ApiResponse<AuthResponse> smsLogin(@RequestBody SmsLoginRequest request) {
        return ApiResponse.success(authService.smsLogin(request));
    }


    @GetMapping("/wechat/mp/qr-login")
    public ApiResponse<WechatMpQrLoginResponse> wechatMpQrLogin() {
        return ApiResponse.success(authService.createWechatMpQrLogin());
    }

    @GetMapping("/wechat/mp/poll")
    public ApiResponse<WechatMpPollResponse> wechatMpPoll(@RequestParam("scene") String scene) {
        return ApiResponse.success(authService.pollWechatMpLogin(scene));
    }

    @GetMapping(value = "/wechat/mp/callback", produces = "text/html;charset=UTF-8")
    public String wechatMpCallback(@RequestParam("code") String code,
                                   @RequestParam("state") String state) {
        return authService.handleWechatMpCallback(code, state);
    }




    @GetMapping("/me")
    public ApiResponse<UserInfoResponse> me(HttpServletRequest request) {
        String token = extractToken(request);
        JwtUtil.JwtPayload payload = jwtUtil.parseToken(token);
        return ApiResponse.success(authService.getUserInfo(payload.getUserId()));
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception e) {
        return ApiResponse.error(e.getMessage());
    }

    private String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");

        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }

        return request.getRemoteAddr();
    }

    private String extractToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");

        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring("Bearer ".length()).trim();
        }

        String token = request.getParameter("token");
        if (token != null && !token.isBlank()) {
            return token;
        }

        return null;
    }
}