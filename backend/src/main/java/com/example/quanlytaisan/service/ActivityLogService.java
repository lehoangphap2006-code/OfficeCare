package com.example.quanlytaisan.service;

import com.example.quanlytaisan.dto.ActivityLogResponse;
import com.example.quanlytaisan.dto.AppNotificationResponse;
import com.example.quanlytaisan.entity.ActivityLog;
import com.example.quanlytaisan.entity.AppNotification;
import com.example.quanlytaisan.repository.ActivityLogRepository;
import com.example.quanlytaisan.repository.AppNotificationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ActivityLogService {
    private final ActivityLogRepository logRepository;
    private final AppNotificationRepository notificationRepository;
    private final ObjectMapper objectMapper;

    public ActivityLogService(
            ActivityLogRepository logRepository,
            AppNotificationRepository notificationRepository,
            ObjectMapper objectMapper) {
        this.logRepository = logRepository;
        this.notificationRepository = notificationRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void record(String actor, String action, String entityType, Object entityId,
            Object oldValue, Object newValue, String alertMessage) {
        ActivityLog log = new ActivityLog();
        log.setActor(actor);
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId == null ? null : String.valueOf(entityId));
        log.setOldValue(serialize(oldValue));
        log.setNewValue(serialize(newValue));
        logRepository.save(log);

        if (alertMessage != null && !alertMessage.isBlank()) {
            AppNotification notification = new AppNotification();
            notification.setUsername(actor);
            notification.setMessage(alertMessage);
            notificationRepository.save(notification);
        }
    }

    @Transactional(readOnly = true)
    public List<ActivityLogResponse> recentLogs() {
        return logRepository.findTop100ByOrderByCreatedAtDesc().stream()
                .map(row -> new ActivityLogResponse(row.getId(), row.getActor(), row.getAction(),
                        row.getEntityType(), row.getEntityId(), row.getOldValue(), row.getNewValue(), row.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AppNotificationResponse> notifications(String username) {
        return notificationRepository.findTop50ByUsernameIgnoreCaseOrderByCreatedAtDesc(username).stream()
                .map(row -> new AppNotificationResponse(row.getId(), row.getMessage(), row.isRead(), row.getCreatedAt()))
                .toList();
    }

    @Transactional
    public void markNotificationRead(Long id, String username) {
        AppNotification notification = notificationRepository.findByIdAndUsernameIgnoreCase(id, username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy thông báo"));
        notification.setRead(true);
        notificationRepository.save(notification);
    }

    @Transactional
    public void markAllNotificationsRead(String username) {
        notificationRepository.findTop50ByUsernameIgnoreCaseOrderByCreatedAtDesc(username).stream()
                .filter(row -> !row.isRead())
                .forEach(row -> row.setRead(true));
    }

    @Transactional
    public void clearNotificationsForUser(String username) {
        notificationRepository.deleteByUsernameIgnoreCase(username);
    }

    private String serialize(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Không thể tuần tự hóa dữ liệu nhật ký hoạt động", exception);
        }
    }
}
