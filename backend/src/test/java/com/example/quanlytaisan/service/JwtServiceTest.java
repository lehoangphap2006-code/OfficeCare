package com.example.quanlytaisan.service;

import com.example.quanlytaisan.entity.AppUser;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtServiceTest {
    @Test
    void generatesDifferentEphemeralKeysWhenSecretIsNotConfigured() {
        JwtService first = new JwtService("", 30, 30);
        JwtService second = new JwtService("", 30, 30);
        AppUser user = new AppUser();
        user.setUsername("employee");
        user.setRole("USER");

        String token = first.createToken(user);

        assertEquals("employee", first.parse(token).getSubject());
        assertThrows(JwtException.class, () -> second.parse(token));
    }

    @Test
    void configuredKeyRemainsStableAcrossServiceInstances() {
        String configuredSecret = Base64.getEncoder().encodeToString(new byte[32]);
        JwtService first = new JwtService(configuredSecret, 30, 30);
        JwtService second = new JwtService(configuredSecret, 30, 30);
        AppUser user = new AppUser();
        user.setUsername("employee");
        user.setRole("USER");

        assertEquals("employee", second.parse(first.createToken(user)).getSubject());
    }
}
