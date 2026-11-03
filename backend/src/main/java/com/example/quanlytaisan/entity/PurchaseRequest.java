package com.example.quanlytaisan.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "purchase_requests")
public class PurchaseRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 50)
    private String requester;

    @Column(nullable = false, length = 150)
    private String assetName;

    private Integer categoryId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Column(precision = 15, scale = 2)
    private BigDecimal estimatedAmount;

    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    @Column(length = 50)
    private String reviewer;

    @Column(length = 500)
    private String reviewNote;

    @Column(unique = true, length = 36)
    private String orderRequestId;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private Instant reviewedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_id")
    private ThuongMaiOrder purchaseOrder;

    public Integer getId() { return id; }
    public String getRequester() { return requester; }
    public void setRequester(String requester) { this.requester = requester; }
    public String getAssetName() { return assetName; }
    public void setAssetName(String assetName) { this.assetName = assetName; }
    public Integer getCategoryId() { return categoryId; }
    public void setCategoryId(Integer categoryId) { this.categoryId = categoryId; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public BigDecimal getEstimatedAmount() { return estimatedAmount; }
    public void setEstimatedAmount(BigDecimal estimatedAmount) { this.estimatedAmount = estimatedAmount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getReviewer() { return reviewer; }
    public void setReviewer(String reviewer) { this.reviewer = reviewer; }
    public String getReviewNote() { return reviewNote; }
    public void setReviewNote(String reviewNote) { this.reviewNote = reviewNote; }
    public String getOrderRequestId() { return orderRequestId; }
    public void setOrderRequestId(String orderRequestId) { this.orderRequestId = orderRequestId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }
    public ThuongMaiOrder getPurchaseOrder() { return purchaseOrder; }
    public void setPurchaseOrder(ThuongMaiOrder purchaseOrder) { this.purchaseOrder = purchaseOrder; }
}
