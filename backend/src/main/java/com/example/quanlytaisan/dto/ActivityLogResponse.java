package com.example.quanlytaisan.dto;

import java.time.Instant;

public record ActivityLogResponse(
        Long id,
        String actor,
        String action,
        String entityType,
        String entityId,
        String oldValue,
        String newValue,
        Instant createdAt) {
}
