package com.example.quanlytaisan.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "thuong_mai_don_hang")
public class ThuongMaiOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true, length = 20)
    private String maDonHang;

    @Column(unique = true, length = 36)
    private String clientRequestId;

    @Column(nullable = false, length = 20)
    private String loaiDon;

    @Column(nullable = false, length = 30)
    private String trangThai;

    @Column(nullable = false, length = 20)
    private String phuongThucThanhToan;

    @Column(nullable = false, length = 20)
    private String trangThaiThanhToan;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal tongTien;

    @Column(nullable = false)
    private Instant taoLuc = Instant.now();

    @Column(length = 40)
    private String maGiaoDich;

    @Column(unique = true, length = 40)
    private String momoRequestId;

    @Column(unique = true, length = 50)
    private String momoOrderId;

    @Column(length = 2000)
    private String momoPayUrl;

    private Instant momoCheckoutCreatedAt;

    private Integer supplierId;

    @Column(length = 150)
    private String supplierName;

    @Column(length = 30)
    private String lyDoThanhLy;

    @Column(length = 1000)
    private String ghiChuThanhLy;

    @Column(length = 1000)
    private String ghiChuDuyetThanhLy;

    private LocalDate ngayThanhLy;

    @Column(length = 30)
    private String phuongThucNhanTien;

    private LocalDate ngayDuKienGiao;

    @Column(length = 50)
    private String nguoiYeuCau;

    @Column(length = 50)
    private String nguoiDuyet;

    private Instant duyetLuc;

    private Instant nhanHangLuc;

    @OneToMany(mappedBy = "donHang", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ThuongMaiOrderItem> chiTiet = new ArrayList<>();

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getMaDonHang() { return maDonHang; }
    public void setMaDonHang(String maDonHang) { this.maDonHang = maDonHang; }
    public String getClientRequestId() { return clientRequestId; }
    public void setClientRequestId(String clientRequestId) { this.clientRequestId = clientRequestId; }
    public String getLoaiDon() { return loaiDon; }
    public void setLoaiDon(String loaiDon) { this.loaiDon = loaiDon; }
    public String getTrangThai() { return trangThai; }
    public void setTrangThai(String trangThai) { this.trangThai = trangThai; }
    public String getPhuongThucThanhToan() { return phuongThucThanhToan; }
    public void setPhuongThucThanhToan(String phuongThucThanhToan) { this.phuongThucThanhToan = phuongThucThanhToan; }
    public String getTrangThaiThanhToan() { return trangThaiThanhToan; }
    public void setTrangThaiThanhToan(String trangThaiThanhToan) { this.trangThaiThanhToan = trangThaiThanhToan; }
    public BigDecimal getTongTien() { return tongTien; }
    public void setTongTien(BigDecimal tongTien) { this.tongTien = tongTien; }
    public Instant getTaoLuc() { return taoLuc; }
    public void setTaoLuc(Instant taoLuc) { this.taoLuc = taoLuc; }
    public String getMaGiaoDich() { return maGiaoDich; }
    public void setMaGiaoDich(String maGiaoDich) { this.maGiaoDich = maGiaoDich; }
    public String getMomoRequestId() { return momoRequestId; }
    public void setMomoRequestId(String momoRequestId) { this.momoRequestId = momoRequestId; }
    public String getMomoOrderId() { return momoOrderId; }
    public void setMomoOrderId(String momoOrderId) { this.momoOrderId = momoOrderId; }
    public String getMomoPayUrl() { return momoPayUrl; }
    public void setMomoPayUrl(String momoPayUrl) { this.momoPayUrl = momoPayUrl; }
    public Instant getMomoCheckoutCreatedAt() { return momoCheckoutCreatedAt; }
    public void setMomoCheckoutCreatedAt(Instant momoCheckoutCreatedAt) { this.momoCheckoutCreatedAt = momoCheckoutCreatedAt; }
    public Integer getSupplierId() { return supplierId; }
    public void setSupplierId(Integer supplierId) { this.supplierId = supplierId; }
    public String getSupplierName() { return supplierName; }
    public void setSupplierName(String supplierName) { this.supplierName = supplierName; }
    public String getLyDoThanhLy() { return lyDoThanhLy; }
    public void setLyDoThanhLy(String lyDoThanhLy) { this.lyDoThanhLy = lyDoThanhLy; }
    public String getGhiChuThanhLy() { return ghiChuThanhLy; }
    public void setGhiChuThanhLy(String ghiChuThanhLy) { this.ghiChuThanhLy = ghiChuThanhLy; }
    public String getGhiChuDuyetThanhLy() { return ghiChuDuyetThanhLy; }
    public void setGhiChuDuyetThanhLy(String ghiChuDuyetThanhLy) { this.ghiChuDuyetThanhLy = ghiChuDuyetThanhLy; }
    public LocalDate getNgayThanhLy() { return ngayThanhLy; }
    public void setNgayThanhLy(LocalDate ngayThanhLy) { this.ngayThanhLy = ngayThanhLy; }
    public String getPhuongThucNhanTien() { return phuongThucNhanTien; }
    public void setPhuongThucNhanTien(String phuongThucNhanTien) { this.phuongThucNhanTien = phuongThucNhanTien; }
    public LocalDate getNgayDuKienGiao() { return ngayDuKienGiao; }
    public void setNgayDuKienGiao(LocalDate ngayDuKienGiao) { this.ngayDuKienGiao = ngayDuKienGiao; }
    public String getNguoiYeuCau() { return nguoiYeuCau; }
    public void setNguoiYeuCau(String nguoiYeuCau) { this.nguoiYeuCau = nguoiYeuCau; }
    public String getNguoiDuyet() { return nguoiDuyet; }
    public void setNguoiDuyet(String nguoiDuyet) { this.nguoiDuyet = nguoiDuyet; }
    public Instant getDuyetLuc() { return duyetLuc; }
    public void setDuyetLuc(Instant duyetLuc) { this.duyetLuc = duyetLuc; }
    public Instant getNhanHangLuc() { return nhanHangLuc; }
    public void setNhanHangLuc(Instant nhanHangLuc) { this.nhanHangLuc = nhanHangLuc; }
    public List<ThuongMaiOrderItem> getChiTiet() { return chiTiet; }
    public void setChiTiet(List<ThuongMaiOrderItem> chiTiet) { this.chiTiet = chiTiet; }
}
