package com.example.quanlytaisan.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "stock_receipt_items")
public class StockReceiptItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receipt_id", nullable = false)
    private StockReceipt receipt;

    @Column(nullable = false)
    private Integer purchaseOrderItemId;

    @Column(nullable = false, length = 150)
    private String productName;

    @Column(nullable = false)
    private Integer orderedQuantity;

    private Integer previouslyReceivedQuantity;

    @Column(nullable = false)
    private Integer receivedQuantity;

    @Column(nullable = false)
    private Integer damagedQuantity;

    @Column(nullable = false)
    private Integer missingQuantity;

    @Column(length = 500)
    private String notes;

    public Integer getId() { return id; }
    public StockReceipt getReceipt() { return receipt; }
    public void setReceipt(StockReceipt receipt) { this.receipt = receipt; }
    public Integer getPurchaseOrderItemId() { return purchaseOrderItemId; }
    public void setPurchaseOrderItemId(Integer purchaseOrderItemId) { this.purchaseOrderItemId = purchaseOrderItemId; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public Integer getOrderedQuantity() { return orderedQuantity; }
    public void setOrderedQuantity(Integer orderedQuantity) { this.orderedQuantity = orderedQuantity; }
    public Integer getPreviouslyReceivedQuantity() {
        return previouslyReceivedQuantity == null ? 0 : previouslyReceivedQuantity;
    }
    public void setPreviouslyReceivedQuantity(Integer previouslyReceivedQuantity) {
        this.previouslyReceivedQuantity = previouslyReceivedQuantity;
    }
    public Integer getReceivedQuantity() { return receivedQuantity; }
    public void setReceivedQuantity(Integer receivedQuantity) { this.receivedQuantity = receivedQuantity; }
    public Integer getDamagedQuantity() { return damagedQuantity; }
    public void setDamagedQuantity(Integer damagedQuantity) { this.damagedQuantity = damagedQuantity; }
    public Integer getMissingQuantity() { return missingQuantity; }
    public void setMissingQuantity(Integer missingQuantity) { this.missingQuantity = missingQuantity; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
