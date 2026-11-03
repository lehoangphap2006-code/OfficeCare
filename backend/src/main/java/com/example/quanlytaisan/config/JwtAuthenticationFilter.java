package com.example.quanlytaisan.config;

import com.example.quanlytaisan.entity.AppUser;
import com.example.quanlytaisan.repository.AppUserRepository;
import com.example.quanlytaisan.repository.RevokedTokenRepository;
import com.example.quanlytaisan.service.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    public static final String AUTH_FAILURE_ATTRIBUTE = "officecare.authenticationFailure";

    private final JwtService jwtService;
    private final AppUserRepository userRepository;
    private final RevokedTokenRepository revokedTokenRepository;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            AppUserRepository userRepository,
            RevokedTokenRepository revokedTokenRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.revokedTokenRepository = revokedTokenRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith("Bearer ")) {
            try {
                Claims claims = jwtService.parse(authorization.substring(7));
                String username = claims.getSubject();
                AppUser user = username == null ? null : userRepository.findByUsernameIgnoreCase(username).orElse(null);
                String tokenType = claims.get("token_type", String.class);
                if (!"access".equals(tokenType)) {
                    request.setAttribute(AUTH_FAILURE_ATTRIBUTE, "Token không phải access token.");
                } else if (claims.getId() == null) {
                    request.setAttribute(AUTH_FAILURE_ATTRIBUTE, "Access token thiếu mã định danh.");
                } else if (user == null || !user.isEnabled()) {
                    request.setAttribute(AUTH_FAILURE_ATTRIBUTE, "Tài khoản không tồn tại hoặc đã bị vô hiệu hóa.");
                } else if (revokedTokenRepository.existsById(claims.getId())) {
                    request.setAttribute(AUTH_FAILURE_ATTRIBUTE, "Access token đã bị thu hồi. Hãy đăng nhập lại.");
                } else {
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            user.getUsername(), null, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole())));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (JwtException | IllegalArgumentException exception) {
                request.setAttribute(AUTH_FAILURE_ATTRIBUTE, "Access token hết hạn hoặc không hợp lệ.");
                SecurityContextHolder.clearContext();
            }
        } else {
            request.setAttribute(AUTH_FAILURE_ATTRIBUTE,
                    authorization == null || authorization.isBlank()
                            ? "Thiếu access token. Hãy đăng nhập lại."
                            : "Header Authorization không hợp lệ.");
        }
        filterChain.doFilter(request, response);
    }
}
