package com.example.quanlytaisan.service;

import com.example.quanlytaisan.dto.LoginResponse;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GoogleOAuthCodeStore {
    private static final long CODE_LIFETIME_SECONDS = 120;
    private final ConcurrentHashMap<String, PendingLogin> pendingLogins = new ConcurrentHashMap<>();

    public String issue(LoginResponse login) {
        Instant now = Instant.now();
        pendingLogins.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
        String code = UUID.randomUUID().toString();
        pendingLogins.put(code, new PendingLogin(login, now.plusSeconds(CODE_LIFETIME_SECONDS)));
        return code;
    }

    public LoginResponse consume(String code) {
        if (code == null || code.isBlank()) return null;
        PendingLogin pending = pendingLogins.remove(code);
        return pending != null && pending.expiresAt().isAfter(Instant.now())
                ? pending.login()
                : null;
    }

    private record PendingLogin(LoginResponse login, Instant expiresAt) {
    }
}
