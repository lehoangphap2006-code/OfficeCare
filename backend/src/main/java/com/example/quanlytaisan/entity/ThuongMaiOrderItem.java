package com.example.quanlytaisan.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "thuong_mai_chi_tiet")
public class ThuongMaiOrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "don_hang_id", nullable = false)
    private ThuongMaiOrder donHang;

    private Integer taiSanId;
    private Integer loaiTaiSanId;
    @Column(length = 20)
    private String loaiDong = "TAI_SAN";
    private Integer consumableId;
    private LocalDate warrantyEndDate;

    @Column(nullable = false, length = 150)
    private String tenSanPham;

    @Column(length = 30)
    private String maTaiSan;

    @Column(length = 100)
    private String toaNha;

    @Column(length = 30)
    private String tang;

    @Column(length = 100)
    private String phong;

    @Column(nullable = false)
    private Integer soLuong;

    private Integer soLuongDaNhan = 0;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal donGia;

    @Column(precision = 15, scale = 2)
    private BigDecimal giaTriConLai;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public ThuongMaiOrder getDonHang() { return donHang; }
    public void setDonHang(ThuongMaiOrder donHang) { this.donHang = donHang; }
    public Integer getTaiSanId() { return taiSanId; }
    public void setTaiSanId(Integer taiSanId) { this.taiSanId = taiSanId; }
    public Integer getLoaiTaiSanId() { return loaiTaiSanId; }
    public void setLoaiTaiSanId(Integer loaiTaiSanId) { this.loaiTaiSanId = loaiTaiSanId; }
    public String getLoaiDong() { return loaiDong == null ? "TAI_SAN" : loaiDong; }
    public void setLoaiDong(String loaiDong) { this.loaiDong = loaiDong; }
    public Integer getConsumableId() { return consumableId; }
    public void setConsumableId(Integer consumableId) { this.consumableId = consumableId; }
    public LocalDate getWarrantyEndDate() { return warrantyEndDate; }
    public void setWarrantyEndDate(LocalDate warrantyEndDate) { this.warrantyEndDate = warrantyEndDate; }
    public String getTenSanPham() { return tenSanPham; }
    public void setTenSanPham(String tenSanPham) { this.tenSanPham = tenSanPham; }
    public String getMaTaiSan() { return maTaiSan; }
    public void setMaTaiSan(String maTaiSan) { this.maTaiSan = maTaiSan; }
    public String getToaNha() { return toaNha; }
    public void setToaNha(String toaNha) { this.toaNha = toaNha; }
    public String getTang() { return tang; }
    public void setTang(String tang) { this.tang = tang; }
    public String getPhong() { return phong; }
    public void setPhong(String phong) { this.phong = phong; }
    public Integer getSoLuong() { return soLuong; }
    public void setSoLuong(Integer soLuong) { this.soLuong = soLuong; }
    public Integer getSoLuongDaNhan() { return soLuongDaNhan == null ? 0 : soLuongDaNhan; }
    public void setSoLuongDaNhan(Integer soLuongDaNhan) { this.soLuongDaNhan = soLuongDaNhan; }
    public BigDecimal getDonGia() { return donGia; }
    public void setDonGia(BigDecimal donGia) { this.donGia = donGia; }
    public BigDecimal getGiaTriConLai() { return giaTriConLai; }
    public void setGiaTriConLai(BigDecimal giaTriConLai) { this.giaTriConLai = giaTriConLai; }
}
