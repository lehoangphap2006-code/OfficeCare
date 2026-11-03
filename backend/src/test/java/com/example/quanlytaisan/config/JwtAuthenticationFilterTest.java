package com.example.quanlytaisan.config;

import com.example.quanlytaisan.repository.AppUserRepository;
import com.example.quanlytaisan.repository.RevokedTokenRepository;
import com.example.quanlytaisan.service.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {
    private final JwtService jwtService = mock(JwtService.class);
    private final AppUserRepository userRepository = mock(AppUserRepository.class);
    private final RevokedTokenRepository revokedTokenRepository = mock(RevokedTokenRepository.class);
    private final TestableJwtAuthenticationFilter filter =
            new TestableJwtAuthenticationFilter(jwtService, userRepository, revokedTokenRepository);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void recordsMissingTokenForUnauthorizedRequestDiagnostics() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();

        filter.filter(request, new MockHttpServletResponse(), mock(FilterChain.class));

        assertEquals("Thiếu access token. Hãy đăng nhập lại.",
                request.getAttribute(JwtAuthenticationFilter.AUTH_FAILURE_ATTRIBUTE));
    }

    @Test
    void rejectsRefreshTokenAsApiAccessToken() throws ServletException, IOException {
        Claims claims = mock(Claims.class);
        when(jwtService.parse("refresh-token")).thenReturn(claims);
        when(claims.get("token_type", String.class)).thenReturn("refresh");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer refresh-token");

        filter.filter(request, new MockHttpServletResponse(), mock(FilterChain.class));

        assertEquals("Token không phải access token.",
                request.getAttribute(JwtAuthenticationFilter.AUTH_FAILURE_ATTRIBUTE));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    private static final class TestableJwtAuthenticationFilter extends JwtAuthenticationFilter {
        private TestableJwtAuthenticationFilter(
                JwtService jwtService,
                AppUserRepository userRepository,
                RevokedTokenRepository revokedTokenRepository) {
            super(jwtService, userRepository, revokedTokenRepository);
        }

        private void filter(
                MockHttpServletRequest request,
                MockHttpServletResponse response,
                FilterChain filterChain) throws ServletException, IOException {
            doFilterInternal(request, response, filterChain);
        }
    }
}
