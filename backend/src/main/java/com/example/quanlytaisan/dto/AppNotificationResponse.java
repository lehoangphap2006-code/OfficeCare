package com.example.quanlytaisan.dto;

import java.time.Instant;

public record AppNotificationResponse(Long id, String message, boolean read, Instant createdAt) {
}
