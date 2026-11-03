package com.example.quanlytaisan.dto;

public record UserProfileResponse(String username, String role, String email, String phoneNumber,
        String fullName, String departmentName, String title) {
}
