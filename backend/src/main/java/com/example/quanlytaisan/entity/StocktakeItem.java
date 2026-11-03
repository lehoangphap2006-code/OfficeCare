package com.example.quanlytaisan.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "stocktake_items", uniqueConstraints = @UniqueConstraint(columnNames = {"session_id", "asset_id"}))
public class StocktakeItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @ManyToOne(optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private StocktakeSession session;
    @ManyToOne(optional = false)
    @JoinColumn(name = "asset_id", nullable = false)
    private TaiSan asset;
    @Column(length = 30)
    private String conditionStatus = "CO_MAT";
    @Column(length = 500)
    private String notes;
    @Column(length = 250)
    private String bookLocation;
    @Column(length = 150)
    private String bookHolder;
    @Column(length = 250)
    private String actualLocation;
    @Column(length = 50)
    private String checkedBy;
    private Instant checkedAt;
    private Integer maintenanceRequestId;

    public Integer getId() { return id; }
    public StocktakeSession getSession() { return session; }
    public void setSession(StocktakeSession session) { this.session = session; }
    public TaiSan getAsset() { return asset; }
    public void setAsset(TaiSan asset) { this.asset = asset; }
    public String getConditionStatus() { return conditionStatus; }
    public void setConditionStatus(String conditionStatus) { this.conditionStatus = conditionStatus; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getBookLocation() { return bookLocation; }
    public void setBookLocation(String bookLocation) { this.bookLocation = bookLocation; }
    public String getBookHolder() { return bookHolder; }
    public void setBookHolder(String bookHolder) { this.bookHolder = bookHolder; }
    public String getActualLocation() { return actualLocation; }
    public void setActualLocation(String actualLocation) { this.actualLocation = actualLocation; }
    public String getCheckedBy() { return checkedBy; }
    public void setCheckedBy(String checkedBy) { this.checkedBy = checkedBy; }
    public Instant getCheckedAt() { return checkedAt; }
    public void setCheckedAt(Instant checkedAt) { this.checkedAt = checkedAt; }
    public Integer getMaintenanceRequestId() { return maintenanceRequestId; }
    public void setMaintenanceRequestId(Integer maintenanceRequestId) { this.maintenanceRequestId = maintenanceRequestId; }
}
