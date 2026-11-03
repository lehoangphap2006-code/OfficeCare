package com.example.quanlytaisan.dto;

import java.time.LocalDateTime;

public record AdminUserResponse(
        Integer userId,
        String username,
        String fullName,
        String role,
        boolean enabled,
        int failedLoginAttempts,
        LocalDateTime lockedUntil,
        Integer employeeId,
        String employeeName) {
}
