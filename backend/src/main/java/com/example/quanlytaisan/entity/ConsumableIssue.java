package com.example.quanlytaisan.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "consumable_issues")
public class ConsumableIssue {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "consumable_id", nullable = false)
    private Consumable consumable;
    @Column
    private Integer departmentId;
    @Column(length = 150)
    private String departmentName;
    @Column(length = 500)
    private String notes;
    @Column(length = 150)
    private String recipient;
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal quantity;
    @Column(nullable = false, length = 50)
    private String issuedBy;
    @Column(nullable = false)
    private Instant issuedAt = Instant.now();
    private LocalDate issueDate;

    public Integer getId() { return id; }
    public Consumable getConsumable() { return consumable; }
    public void setConsumable(Consumable consumable) { this.consumable = consumable; }
    public Integer getDepartmentId() { return departmentId; }
    public void setDepartmentId(Integer departmentId) { this.departmentId = departmentId; }
    public String getDepartmentName() { return departmentName; }
    public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getRecipient() { return recipient; }
    public void setRecipient(String recipient) { this.recipient = recipient; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public String getIssuedBy() { return issuedBy; }
    public void setIssuedBy(String issuedBy) { this.issuedBy = issuedBy; }
    public Instant getIssuedAt() { return issuedAt; }
    public void setIssuedAt(Instant issuedAt) { this.issuedAt = issuedAt; }
    public LocalDate getIssueDate() { return issueDate == null ? issuedAt.atZone(java.time.ZoneId.systemDefault()).toLocalDate() : issueDate; }
    public void setIssueDate(LocalDate issueDate) { this.issueDate = issueDate; }
}
