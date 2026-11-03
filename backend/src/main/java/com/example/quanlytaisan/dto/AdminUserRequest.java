package com.example.quanlytaisan.dto;

public record AdminUserRequest(
        String username,
        String password,
        String role,
        Boolean enabled,
        String fullName,
        Integer employeeId) {
}
