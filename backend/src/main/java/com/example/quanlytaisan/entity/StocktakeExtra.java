package com.example.quanlytaisan.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "stocktake_extras", uniqueConstraints = @UniqueConstraint(columnNames = {"session_id", "asset_code"}))
public class StocktakeExtra {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private StocktakeSession session;

    @Column(nullable = false, length = 30)
    private String assetCode;

    @Column(nullable = false, length = 150)
    private String assetName;

    @Column(length = 250)
    private String actualLocation;

    @Column(length = 500)
    private String notes;

    @Column(length = 50)
    private String checkedBy;

    @Column(nullable = false)
    private Instant checkedAt = Instant.now();

    public Integer getId() { return id; }
    public StocktakeSession getSession() { return session; }
    public void setSession(StocktakeSession session) { this.session = session; }
    public String getAssetCode() { return assetCode; }
    public void setAssetCode(String assetCode) { this.assetCode = assetCode; }
    public String getAssetName() { return assetName; }
    public void setAssetName(String assetName) { this.assetName = assetName; }
    public String getActualLocation() { return actualLocation; }
    public void setActualLocation(String actualLocation) { this.actualLocation = actualLocation; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getCheckedBy() { return checkedBy; }
    public void setCheckedBy(String checkedBy) { this.checkedBy = checkedBy; }
    public Instant getCheckedAt() { return checkedAt; }
}
