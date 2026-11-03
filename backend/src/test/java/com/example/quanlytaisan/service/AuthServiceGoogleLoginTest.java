package com.example.quanlytaisan.service;

import com.example.quanlytaisan.dto.LoginResponse;
import com.example.quanlytaisan.entity.AppUser;
import com.example.quanlytaisan.repository.AdminMasterDataRepository;
import com.example.quanlytaisan.repository.AppUserRepository;
import com.example.quanlytaisan.repository.RevokedTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceGoogleLoginTest {
    @Mock private AppUserRepository userRepository;
    @Mock private AdminMasterDataRepository masterDataRepository;
    @Mock private RevokedTokenRepository revokedTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private ActivityLogService activityLogService;

    @Test
    void createsGoogleUserWithEmployeeRole() {
        AuthService service = new AuthService(userRepository, masterDataRepository, revokedTokenRepository,
                passwordEncoder, jwtService, activityLogService, 5, 15);
        when(userRepository.findByGoogleSubject("google-subject")).thenReturn(Optional.empty());
        when(userRepository.existsByUsernameIgnoreCase(any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("encoded-random-password");
        when(userRepository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.createToken(any(AppUser.class))).thenReturn("access-token");
        when(jwtService.createRefreshToken(any(AppUser.class))).thenReturn("refresh-token");
        when(jwtService.getExpirationSeconds()).thenReturn(28800L);

        LoginResponse response = service.loginWithGoogle("google-subject", "employee@example.com", "Employee Name");

        ArgumentCaptor<AppUser> userCaptor = ArgumentCaptor.forClass(AppUser.class);
        verify(userRepository).save(userCaptor.capture());
        AppUser created = userCaptor.getValue();
        assertEquals("USER", created.getRole());
        assertEquals("google-subject", created.getGoogleSubject());
        assertEquals("employee@example.com", created.getEmail());
        assertEquals("Employee Name", created.getFullName());
        assertEquals("USER", response.role());
        assertEquals("access-token", response.accessToken());
    }
}
