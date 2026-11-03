package com.example.quanlytaisan.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "admin_invoice_attachments")
public class AdminInvoiceAttachment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false, unique = true)
    private AdminInvoice invoice;

    @Column(nullable = false, length = 255)
    private String fileName;

    @Column(nullable = false, length = 100)
    private String contentType;

    @Lob
    @Column(nullable = false)
    private byte[] data;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public Integer getId() { return id; }
    public AdminInvoice getInvoice() { return invoice; }
    public void setInvoice(AdminInvoice invoice) { this.invoice = invoice; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public byte[] getData() { return data; }
    public void setData(byte[] data) { this.data = data; }
    public Instant getCreatedAt() { return createdAt; }
}
