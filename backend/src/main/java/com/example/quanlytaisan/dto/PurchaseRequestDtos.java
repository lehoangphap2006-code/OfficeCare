package com.example.quanlytaisan.dto;

import java.time.Instant;
import java.math.BigDecimal;
import java.time.LocalDate;

public final class PurchaseRequestDtos {
    private PurchaseRequestDtos() {}
    public record CreateRequest(String assetName, Integer categoryId, Integer quantity, String reason,
            BigDecimal estimatedAmount) {}
    public record ReviewRequest(String status, String reviewNote) {}
    public record Response(Integer id, String requester, String assetName, Integer categoryId,
            String categoryName, Integer quantity, String reason, BigDecimal estimatedAmount,
            String status, String reviewer, String reviewNote, Instant createdAt, Instant reviewedAt,
            Integer purchaseOrderId, String purchaseOrderNumber, String purchaseOrderStatus) {}
    public record PlaceOrderRequest(Integer supplierId, LocalDate expectedDeliveryDate,
            String paymentMethod, BigDecimal unitPrice, Integer locationId, LocalDate warrantyEndDate,
            String requestId) {
        public PlaceOrderRequest(Integer supplierId, LocalDate expectedDeliveryDate,
                String paymentMethod, BigDecimal unitPrice, Integer locationId, LocalDate warrantyEndDate) {
            this(supplierId, expectedDeliveryDate, paymentMethod, unitPrice, locationId, warrantyEndDate, null);
        }
    }
}
