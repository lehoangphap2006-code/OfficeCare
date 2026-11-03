package com.example.quanlytaisan.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "admin_invoices")
public class AdminInvoice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Column(nullable = false, unique = true, length = 50)
    private String invoiceNumber;
    @Column(nullable = false)
    private LocalDate invoiceDate;
    @Column(nullable = false, precision = 16, scale = 2)
    private BigDecimal totalAmount;
    private Integer purchaseAssetId;
    private Integer supplierId;
    @Column(length = 150)
    private String supplierName;
    @Column(length = 500)
    private String description;
    private Integer purchaseOrderId;
    @Column(nullable = false, length = 30)
    private String paymentStatus = "CHUA_TRA";
    @Column(length = 255)
    private String attachmentName;
    @Column(length = 100)
    private String attachmentContentType;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL)
    private List<AdminInvoiceLine> invoiceLines = new ArrayList<>();

    public Integer getId() { return id; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }
    public LocalDate getInvoiceDate() { return invoiceDate; }
    public void setInvoiceDate(LocalDate invoiceDate) { this.invoiceDate = invoiceDate; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public Integer getPurchaseAssetId() { return purchaseAssetId; }
    public void setPurchaseAssetId(Integer purchaseAssetId) { this.purchaseAssetId = purchaseAssetId; }
    public Integer getSupplierId() { return supplierId; }
    public void setSupplierId(Integer supplierId) { this.supplierId = supplierId; }
    public String getSupplierName() { return supplierName; }
    public void setSupplierName(String supplierName) { this.supplierName = supplierName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getPurchaseOrderId() { return purchaseOrderId; }
    public void setPurchaseOrderId(Integer purchaseOrderId) { this.purchaseOrderId = purchaseOrderId; }
    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }
    public String getAttachmentName() { return attachmentName; }
    public void setAttachmentName(String attachmentName) { this.attachmentName = attachmentName; }
    public String getAttachmentContentType() { return attachmentContentType; }
    public void setAttachmentContentType(String attachmentContentType) { this.attachmentContentType = attachmentContentType; }
    public List<AdminInvoiceLine> getInvoiceLines() { return invoiceLines; }
}
