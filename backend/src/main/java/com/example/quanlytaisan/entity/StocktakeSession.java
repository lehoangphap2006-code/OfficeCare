package com.example.quanlytaisan.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.Arrays;

@Entity
@Table(name = "stocktake_sessions")
public class StocktakeSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(nullable = false)
    private LocalDate stocktakeDate;
    @Column(length = 150)
    private String department;
    @Column(length = 20)
    private String scopeType;
    @Column(length = 250)
    private String scopeValue;
    private LocalDate endDate;
    @Column(nullable = false, length = 30)
    private String status = "DRAFT";
    @Column(length = 50)
    private String responsibleUsername;
    @Column(length = 150)
    private String checkerName;
    @Column(length = 150)
    private String confirmerName;
    @Lob
    @Column(name = "report_pdf")
    private byte[] reportPdf;

    public Integer getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public LocalDate getStocktakeDate() { return stocktakeDate; }
    public void setStocktakeDate(LocalDate stocktakeDate) { this.stocktakeDate = stocktakeDate; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public String getScopeType() {
        return scopeType == null ? (department == null ? "ALL" : "DEPARTMENT") : scopeType;
    }
    public void setScopeType(String scopeType) { this.scopeType = scopeType; }
    public String getScopeValue() { return scopeValue == null ? department : scopeValue; }
    public void setScopeValue(String scopeValue) { this.scopeValue = scopeValue; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getResponsibleUsername() { return responsibleUsername; }
    public void setResponsibleUsername(String responsibleUsername) { this.responsibleUsername = responsibleUsername; }
    public String getCheckerName() { return checkerName; }
    public void setCheckerName(String checkerName) { this.checkerName = checkerName; }
    public String getConfirmerName() { return confirmerName; }
    public void setConfirmerName(String confirmerName) { this.confirmerName = confirmerName; }
    public byte[] getReportPdf() { return reportPdf == null ? null : Arrays.copyOf(reportPdf, reportPdf.length); }
    public void setReportPdf(byte[] reportPdf) { this.reportPdf = reportPdf == null ? null : Arrays.copyOf(reportPdf, reportPdf.length); }
}
