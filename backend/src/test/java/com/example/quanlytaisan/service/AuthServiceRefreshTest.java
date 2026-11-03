package com.example.quanlytaisan.service;

import com.example.quanlytaisan.entity.AppUser;
import com.example.quanlytaisan.repository.AdminMasterDataRepository;
import com.example.quanlytaisan.entity.RevokedToken;
import com.example.quanlytaisan.repository.AppUserRepository;
import com.example.quanlytaisan.repository.RevokedTokenRepository;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class AuthServiceRefreshTest {
    private AppUserRepository userRepository;
    private RevokedTokenRepository revokedTokenRepository;
    private JwtService jwtService;
    private AuthService service;
    private Claims claims;
    private AppUser user;

    @BeforeEach
    void setUp() {
        userRepository = mock(AppUserRepository.class);
        revokedTokenRepository = mock(RevokedTokenRepository.class);
        jwtService = mock(JwtService.class);
        service = new AuthService(
                userRepository,
                mock(AdminMasterDataRepository.class),
                revokedTokenRepository,
                mock(PasswordEncoder.class),
                jwtService,
                mock(ActivityLogService.class),
                5,
                15);
        claims = mock(Claims.class);
        user = new AppUser();
        user.setUsername("admin");
        user.setRole("ADMIN");
        when(jwtService.parse("current-token")).thenReturn(claims);
        when(claims.getId()).thenReturn("token-id");
        when(claims.getSubject()).thenReturn("admin");
        when(claims.get("token_type", String.class)).thenReturn("refresh");
        when(claims.getExpiration()).thenReturn(new java.util.Date(System.currentTimeMillis() + 60_000));
        when(revokedTokenRepository.existsById("token-id")).thenReturn(false);
        when(userRepository.findByUsernameIgnoreCase("admin")).thenReturn(Optional.of(user));
        when(jwtService.createToken(user)).thenReturn("renewed-token");
        when(jwtService.createRefreshToken(user)).thenReturn("next-refresh-token");
        when(jwtService.getExpirationSeconds()).thenReturn(28_800L);
    }

    @Test
    void refreshesAnActiveUsersValidToken() {
        var response = service.refresh("current-token");

        assertEquals("renewed-token", response.accessToken());
        assertEquals("next-refresh-token", response.refreshToken());
        assertEquals("admin", response.username());
        assertEquals("ADMIN", response.role());
        assertEquals(28_800L, response.expiresInSeconds());
        verify(revokedTokenRepository).save(argThat((RevokedToken revoked) -> revoked.getTokenId().equals("token-id")));
    }

    @Test
    void rejectsRevokedTokens() {
        when(revokedTokenRepository.existsById("token-id")).thenReturn(true);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class, () -> service.refresh("current-token"));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        verify(jwtService, never()).createToken(any());
    }

    @Test
    void rejectsDisabledAccounts() {
        user.setEnabled(false);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class, () -> service.refresh("current-token"));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        verify(jwtService, never()).createToken(any());
    }

    @Test
    void rejectsRefreshTokensForUnknownUsers() {
        when(userRepository.findByUsernameIgnoreCase("admin")).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class, () -> service.refresh("current-token"));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        verify(jwtService, never()).createToken(any());
    }

    @Test
    void rejectsAccessTokensUsedAsRefreshTokens() {
        when(claims.get("token_type", String.class)).thenReturn("access");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class, () -> service.refresh("current-token"));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        verify(userRepository, never()).findByUsernameIgnoreCase(any());
        verify(jwtService, never()).createToken(any());
    }
}
