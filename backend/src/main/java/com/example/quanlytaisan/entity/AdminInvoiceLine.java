package com.example.quanlytaisan.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "admin_invoice_lines")
public class AdminInvoiceLine {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private AdminInvoice invoice;

    @Column(nullable = false, length = 20)
    private String itemType;

    @Column(nullable = false, length = 200)
    private String itemName;

    @Column(nullable = false, precision = 16, scale = 3)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 16, scale = 2)
    private BigDecimal unitPrice;

    private Integer assetId;
    private Integer consumableId;
    private Integer purchaseOrderItemId;

    public Integer getId() { return id; }
    public AdminInvoice getInvoice() { return invoice; }
    public void setInvoice(AdminInvoice invoice) { this.invoice = invoice; }
    public String getItemType() { return itemType; }
    public void setItemType(String itemType) { this.itemType = itemType; }
    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public Integer getAssetId() { return assetId; }
    public void setAssetId(Integer assetId) { this.assetId = assetId; }
    public Integer getConsumableId() { return consumableId; }
    public void setConsumableId(Integer consumableId) { this.consumableId = consumableId; }
    public Integer getPurchaseOrderItemId() { return purchaseOrderItemId; }
    public void setPurchaseOrderItemId(Integer purchaseOrderItemId) { this.purchaseOrderItemId = purchaseOrderItemId; }
}
