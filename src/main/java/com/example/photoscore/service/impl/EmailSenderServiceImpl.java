package com.example.photoscore.service.impl;

import com.example.photoscore.service.EmailSenderService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailSenderServiceImpl implements EmailSenderService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String from;

    @Override
    public void sendRegisterCode(String email, String code) {
        if (from == null || from.isBlank()) {
            throw new RuntimeException("邮箱服务未配置，请检查 MAIL_USERNAME / MAIL_PASSWORD");
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("PhotoScore Pro 邮箱验证码");
        message.setText("""
                你好，欢迎使用 PhotoScore Pro。

                你的邮箱验证码是：%s

                验证码 5 分钟内有效，请勿泄露给他人。

                如果不是你本人操作，请忽略本邮件。
                """.formatted(code));

        mailSender.send(message);
    }
}