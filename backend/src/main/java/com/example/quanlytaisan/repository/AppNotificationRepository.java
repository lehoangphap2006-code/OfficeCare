package com.example.quanlytaisan.repository;

import com.example.quanlytaisan.entity.AppNotification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppNotificationRepository extends JpaRepository<AppNotification, Long> {
    List<AppNotification> findTop50ByUsernameIgnoreCaseOrderByCreatedAtDesc(String username);
    Optional<AppNotification> findByIdAndUsernameIgnoreCase(Long id, String username);
    void deleteByUsernameIgnoreCase(String username);
}
