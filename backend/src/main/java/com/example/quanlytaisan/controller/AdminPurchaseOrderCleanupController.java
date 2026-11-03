package com.example.quanlytaisan.controller;

import com.example.quanlytaisan.service.PurchaseOrderCleanupService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/purchase-orders")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPurchaseOrderCleanupController {
    private final PurchaseOrderCleanupService cleanupService;

    public AdminPurchaseOrderCleanupController(PurchaseOrderCleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    @DeleteMapping("/unreceived")
    public PurchaseOrderCleanupService.CleanupResult deleteUnreceived(Authentication authentication) {
        return cleanupService.deleteUnreceivedPurchaseOrders(authentication.getName());
    }
}
