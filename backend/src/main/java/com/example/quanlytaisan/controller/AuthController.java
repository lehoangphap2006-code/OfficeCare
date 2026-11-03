package com.example.quanlytaisan.controller;

import com.example.quanlytaisan.dto.AuthRegisterRequest;
import com.example.quanlytaisan.dto.ChangePasswordRequest;
import com.example.quanlytaisan.dto.CurrentUserResponse;
import com.example.quanlytaisan.dto.GoogleLoginExchangeRequest;
import com.example.quanlytaisan.dto.GoogleLoginStatus;
import com.example.quanlytaisan.dto.LoginRequest;
import com.example.quanlytaisan.dto.LoginResponse;
import com.example.quanlytaisan.dto.UserProfileRequest;
import com.example.quanlytaisan.dto.UserProfileResponse;
import com.example.quanlytaisan.service.AuthService;
import com.example.quanlytaisan.service.GoogleOAuthCodeStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final GoogleOAuthCodeStore googleOAuthCodeStore;
    private final boolean googleOAuthEnabled;

    public AuthController(
            AuthService authService,
            GoogleOAuthCodeStore googleOAuthCodeStore,
            @Value("${app.security.oauth.google.enabled:false}") boolean googleOAuthEnabled) {
        this.authService = authService;
        this.googleOAuthCodeStore = googleOAuthCodeStore;
        this.googleOAuthEnabled = googleOAuthEnabled;
    }

    @GetMapping("/google/status")
    public GoogleLoginStatus googleLoginStatus() {
        return new GoogleLoginStatus(googleOAuthEnabled);
    }

    @PostMapping("/google/exchange")
    public LoginResponse exchangeGoogleLoginCode(@RequestBody GoogleLoginExchangeRequest request) {
        if (!googleOAuthEnabled) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Đăng nhập Google chưa được cấu hình");
        }
        LoginResponse login = googleOAuthCodeStore.consume(request == null ? null : request.code());
        if (login == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Mã đăng nhập Google đã hết hạn hoặc đã được sử dụng");
        }
        return login;
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    public LoginResponse refresh(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ");
        }
        return authService.refresh(authorization.substring(7));
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public LoginResponse register(@RequestBody AuthRegisterRequest request) {
        return authService.register(request);
    }

    @GetMapping("/me")
    public CurrentUserResponse currentUser(Authentication authentication) {
        return authService.currentUser(authentication.getName());
    }

    @GetMapping("/profile")
    public UserProfileResponse profile(Authentication authentication) {
        return authService.profile(authentication.getName());
    }

    @PutMapping("/profile")
    public UserProfileResponse updateProfile(@RequestBody UserProfileRequest request, Authentication authentication) {
        return authService.updateProfile(authentication.getName(), request);
    }

    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@RequestBody ChangePasswordRequest request, Authentication authentication) {
        authService.changePassword(authentication.getName(), request);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader(value = "X-Refresh-Token", required = false) String refreshToken) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Thiếu bearer token");
        }
        authService.logout(authorization.substring(7), refreshToken);
    }
}
