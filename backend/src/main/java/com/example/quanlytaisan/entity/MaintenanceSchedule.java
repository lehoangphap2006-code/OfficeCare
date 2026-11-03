package com.example.quanlytaisan.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "maintenance_schedule")
public class MaintenanceSchedule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tai_san_id", nullable = false)
    private TaiSan taiSan;

    @Column(nullable = false)
    private Integer chuKyThang;

    @Column(nullable = false)
    private LocalDate ngayBatDau;

    private LocalDate ngayBaoTriGanNhat;

    @Column(nullable = false, length = 20)
    private String trangThai = "ACTIVE";

    @Column(nullable = false)
    private Instant taoLuc = Instant.now();

    public Integer getId() { return id; }
    public TaiSan getTaiSan() { return taiSan; }
    public void setTaiSan(TaiSan taiSan) { this.taiSan = taiSan; }
    public Integer getChuKyThang() { return chuKyThang; }
    public void setChuKyThang(Integer chuKyThang) { this.chuKyThang = chuKyThang; }
    public LocalDate getNgayBatDau() { return ngayBatDau; }
    public void setNgayBatDau(LocalDate ngayBatDau) { this.ngayBatDau = ngayBatDau; }
    public LocalDate getNgayBaoTriGanNhat() { return ngayBaoTriGanNhat; }
    public void setNgayBaoTriGanNhat(LocalDate ngayBaoTriGanNhat) { this.ngayBaoTriGanNhat = ngayBaoTriGanNhat; }
    public String getTrangThai() { return trangThai; }
    public void setTrangThai(String trangThai) { this.trangThai = trangThai; }
}
