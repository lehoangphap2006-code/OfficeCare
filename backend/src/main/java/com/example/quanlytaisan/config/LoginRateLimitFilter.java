package com.example.quanlytaisan.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class LoginRateLimitFilter extends OncePerRequestFilter {
    private final ConcurrentHashMap<String, AttemptWindow> attemptsByAddress = new ConcurrentHashMap<>();
    private final AtomicLong requestCount = new AtomicLong();
    private final int maxAttempts;
    private final long windowMillis;
    private final int maxTrackedAddresses;

    public LoginRateLimitFilter(
            @Value("${app.security.login.ip-max-attempts}") int maxAttempts,
            @Value("${app.security.login.ip-window-seconds}") long windowSeconds,
            @Value("${app.security.login.ip-max-tracked-addresses}") int maxTrackedAddresses) {
        if (maxAttempts < 1 || windowSeconds < 1 || maxTrackedAddresses < 1) {
            throw new IllegalArgumentException("Cấu hình giới hạn đăng nhập theo IP phải lớn hơn 0");
        }
        this.maxAttempts = maxAttempts;
        this.windowMillis = windowSeconds * 1000;
        this.maxTrackedAddresses = maxTrackedAddresses;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod())
                || !"/api/auth/login".equals(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        long now = System.currentTimeMillis();
        if (requestCount.incrementAndGet() % 128 == 0) {
            removeExpiredWindows(now);
        }

        String address = request.getRemoteAddr();
        if (address == null || address.isBlank()) {
            address = "unknown";
        }

        AttemptWindow window = attemptsByAddress.get(address);
        if (window == null) {
            synchronized (attemptsByAddress) {
                window = attemptsByAddress.get(address);
                if (window == null) {
                    removeExpiredWindows(now);
                    if (attemptsByAddress.size() >= maxTrackedAddresses) {
                        reject(response, windowMillis);
                        return;
                    }
                    window = new AttemptWindow(now);
                    attemptsByAddress.put(address, window);
                }
            }
        }

        long retryAfterMillis = window.acquire(now, windowMillis, maxAttempts);
        if (retryAfterMillis > 0) {
            reject(response, retryAfterMillis);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void removeExpiredWindows(long now) {
        attemptsByAddress.entrySet().removeIf(entry -> entry.getValue().isExpired(now, windowMillis));
    }

    private void reject(HttpServletResponse response, long retryAfterMillis) throws IOException {
        long retryAfterSeconds = Math.max(1, (retryAfterMillis + 999) / 1000);
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", Long.toString(retryAfterSeconds));
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"message\":\"Quá nhiều lần đăng nhập. Vui lòng thử lại sau.\"}");
    }

    private static final class AttemptWindow {
        private long startedAt;
        private int attempts;

        private AttemptWindow(long startedAt) {
            this.startedAt = startedAt;
        }

        private synchronized long acquire(long now, long windowMillis, int maxAttempts) {
            if (isExpired(now, windowMillis)) {
                startedAt = now;
                attempts = 0;
            }
            if (attempts >= maxAttempts) {
                return startedAt + windowMillis - now;
            }
            attempts++;
            return 0;
        }

        private synchronized boolean isExpired(long now, long windowMillis) {
            return now - startedAt >= windowMillis;
        }
    }
}
