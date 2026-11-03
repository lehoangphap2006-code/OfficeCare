package com.example.quanlytaisan.config;

import com.example.quanlytaisan.dto.LoginResponse;
import com.example.quanlytaisan.service.AuthService;
import com.example.quanlytaisan.service.GoogleOAuthCodeStore;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

@Component
public class GoogleOAuthLoginSuccessHandler
        implements AuthenticationSuccessHandler, AuthenticationFailureHandler {
    private final AuthService authService;
    private final GoogleOAuthCodeStore codeStore;
    private final String frontendUrl;

    public GoogleOAuthLoginSuccessHandler(
            AuthService authService,
            GoogleOAuthCodeStore codeStore,
            @Value("${app.security.oauth.google.frontend-url:http://localhost:5500}") String frontendUrl) {
        this.authService = authService;
        this.codeStore = codeStore;
        this.frontendUrl = frontendUrl.endsWith("/") ? frontendUrl : frontendUrl + "/";
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {
        if (!(authentication.getPrincipal() instanceof OAuth2User principal)) {
            onAuthenticationFailure(request, response, null);
            return;
        }
        Map<String, Object> attributes = principal.getAttributes();
        String subject = stringAttribute(attributes, "sub");
        String email = stringAttribute(attributes, "email");
        Object verified = attributes.get("email_verified");
        if (subject == null || email == null || !Boolean.TRUE.equals(verified)) {
            onAuthenticationFailure(request, response, null);
            return;
        }

        LoginResponse login = authService.loginWithGoogle(subject, email, stringAttribute(attributes, "name"));
        response.sendRedirect(frontendUrl + "#google_code=" + codeStore.issue(login));
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            org.springframework.security.core.AuthenticationException exception) throws IOException {
        response.sendRedirect(frontendUrl + "#google_error=1");
    }

    private String stringAttribute(Map<String, Object> attributes, String name) {
        Object value = attributes.get(name);
        return value instanceof String text && !text.isBlank() ? text : null;
    }
}
