package com.example.quanlytaisan.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "asset_return_requests")
public class AssetReturnRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false)
    private TaiSanCapPhat assignment;

    @Column(nullable = false, length = 50)
    private String requester;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private Instant completedAt;

    public Integer getId() { return id; }
    public TaiSanCapPhat getAssignment() { return assignment; }
    public void setAssignment(TaiSanCapPhat assignment) { this.assignment = assignment; }
    public String getRequester() { return requester; }
    public void setRequester(String requester) { this.requester = requester; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
