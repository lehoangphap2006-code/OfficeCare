package com.example.quanlytaisan.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "loai_tai_san")
public class LoaiTaiSan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer loaiId;

    @Column(nullable = false, unique = true, length = 100)
    private String tenLoai;

    @Column
    private Integer thoiGianKhauHaoNam = 5;

    @Column(name = "requires_serial")
    private Boolean requiresSerial = false;

    public Integer getLoaiId() { return loaiId; }
    public void setLoaiId(Integer loaiId) { this.loaiId = loaiId; }
    public String getTenLoai() { return tenLoai; }
    public void setTenLoai(String tenLoai) { this.tenLoai = tenLoai; }
    public Integer getThoiGianKhauHaoNam() { return thoiGianKhauHaoNam == null ? 5 : thoiGianKhauHaoNam; }
    public void setThoiGianKhauHaoNam(Integer thoiGianKhauHaoNam) { this.thoiGianKhauHaoNam = thoiGianKhauHaoNam; }
    public boolean isRequiresSerial() { return Boolean.TRUE.equals(requiresSerial); }
    public void setRequiresSerial(Boolean requiresSerial) { this.requiresSerial = Boolean.TRUE.equals(requiresSerial); }
}
