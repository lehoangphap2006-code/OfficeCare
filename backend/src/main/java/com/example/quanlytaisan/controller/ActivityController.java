package com.example.quanlytaisan.controller;

import com.example.quanlytaisan.dto.ActivityLogResponse;
import com.example.quanlytaisan.dto.AppNotificationResponse;
import com.example.quanlytaisan.service.ActivityLogService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ActivityController {
    private final ActivityLogService activityLogService;

    public ActivityController(ActivityLogService activityLogService) {
        this.activityLogService = activityLogService;
    }

    @GetMapping("/nhat-ky")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public List<ActivityLogResponse> recentLogs() {
        return activityLogService.recentLogs();
    }

    @GetMapping("/thong-bao")
    public List<AppNotificationResponse> notifications(Authentication authentication) {
        return activityLogService.notifications(authentication.getName());
    }

    @PutMapping("/thong-bao/{id}/da-xem")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@PathVariable Long id, Authentication authentication) {
        activityLogService.markNotificationRead(id, authentication.getName());
    }

    @PutMapping("/thong-bao/da-xem-tat-ca")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllRead(Authentication authentication) {
        activityLogService.markAllNotificationsRead(authentication.getName());
    }
}
