package com.example.photoscore.security;

import com.example.photoscore.pojo.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;
    private final AdminAccessService adminAccessService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();

        /*
         * 只过滤需要登录保护的业务接口。
         *
         * 原来这里只处理 /api/photo-score/，
         * 所以 /api/pro-tools/ 被跳过，导致 SecurityConfig 判断未登录并返回 403。
         */
        return !(path.startsWith("/api/photo-score/")
                || path.startsWith("/api/pro-tools/")
                || path.startsWith("/api/workspace/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws IOException {
        String token = extractToken(request);

        try {
            JwtUtil.JwtPayload payload = jwtUtil.parseToken(token);

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            payload.getUsername(),
                            null,
                            adminAccessService.isAdmin(payload.getUsername())
                                    ? AuthorityUtils.createAuthorityList("ROLE_USER", "ROLE_ADMIN")
                                    : AuthorityUtils.createAuthorityList("ROLE_USER")
                    );

            authentication.setDetails(payload);

            SecurityContextHolder.getContext().setAuthentication(authentication);

            // 兼容你后续如果想在业务代码里直接取当前用户
            UserContext.set(payload.getUserId(), payload.getUsername());

            filterChain.doFilter(request, response);

        } catch (Exception e) {
            SecurityContextHolder.clearContext();
            UserContext.clear();

            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");

            response.getWriter().write(objectMapper.writeValueAsString(
                    ApiResponse.unauthorized("请先登录")
            ));
        } finally {
            UserContext.clear();
        }
    }

    private String extractToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");

        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring("Bearer ".length()).trim();
        }

        // 支持下载链接使用 ?token=xxx
        String tokenParam = request.getParameter("token");
        if (tokenParam != null && !tokenParam.isBlank()) {
            return tokenParam.trim();
        }

        return null;
    }
}
