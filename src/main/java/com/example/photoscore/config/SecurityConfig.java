package com.example.photoscore.config;


import com.example.photoscore.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 前后端分离 / 静态 HTML + API 模式，关闭 CSRF
                .csrf(AbstractHttpConfigurer::disable)

                // 不使用 Session，完全使用 JWT
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // 禁用默认表单登录
                .formLogin(AbstractHttpConfigurer::disable)

                // 禁用 HTTP Basic
                .httpBasic(AbstractHttpConfigurer::disable)

                .authorizeHttpRequests(auth -> auth
                        // 预检请求放行
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // 首页和登录页放行
                        .requestMatchers("/", "/index.html", "/login.html").permitAll()

                        // 静态资源放行
                        .requestMatchers(
                                "/",
                                "/index.html",
                                "/login.html",
                                "/favicon.ico",
                                "/css/**",
                                "/js/**",
                                "/images/**",
                                "/assets/**",
                                "/uploads/**"
                        ).permitAll()

                        // 登录注册接口放行
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/share/**").permitAll()
                        // 健康检查放行
                        .requestMatchers("/api/health").permitAll()
                        .requestMatchers("/api/monitor/**").permitAll()
                        // 照片评分相关接口必须登录
                        .requestMatchers("/api/pro-tools/**").authenticated()
                        .requestMatchers("/api/workspace/**").authenticated()
                        .requestMatchers("/api/photo-score/**").authenticated()

                        // 其他接口默认放行，避免影响你现有项目
                        .anyRequest().permitAll()
                )

                // JWT 过滤器放在用户名密码过滤器之前
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

                .cors(Customizer.withDefaults());

        return http.build();
    }
}