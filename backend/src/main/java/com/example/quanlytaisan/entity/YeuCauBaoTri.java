package com.example.quanlytaisan.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "yeu_cau_bao_tri")
public class YeuCauBaoTri {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tai_san_id", nullable = false)
    private TaiSan taiSan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lich_bao_tri_id")
    private MaintenanceSchedule lichBaoTri;

    @Column(nullable = false, length = 150)
    private String tieuDe;

    @Column(length = 1000)
    private String moTa;

    @Column(length = 50)
    private String nguoiYeuCau;

    @Column(length = 1000)
    private String anhSuCo;

    @Column(nullable = false, length = 20)
    private String trangThai;

    @Column(length = 30)
    private String trangThaiTruocBaoTri;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal chiPhi = BigDecimal.ZERO;

    @Column(nullable = false)
    private Instant taoLuc = Instant.now();

    private Instant hoanTatLuc;

    @Column(length = 100)
    private String nguoiThucHien;

    private LocalDate ngayHoanThanh;

    private LocalDate ngayBaoHong;

    @Column(length = 20)
    private String mucDo;

    @Column(length = 20)
    private String noiXuLy;

    @Column(length = 1000)
    private String ketQua;

    @Column(length = 30)
    private String loaiYeuCau = "SUA_CHUA";

    @Column(length = 30)
    private String nguonTao;

    @Column(length = 100)
    private String phongBan;

    public Integer getId() { return id; }
    public TaiSan getTaiSan() { return taiSan; }
    public void setTaiSan(TaiSan taiSan) { this.taiSan = taiSan; }
    public MaintenanceSchedule getLichBaoTri() { return lichBaoTri; }
    public void setLichBaoTri(MaintenanceSchedule lichBaoTri) { this.lichBaoTri = lichBaoTri; }
    public String getTieuDe() { return tieuDe; }
    public void setTieuDe(String tieuDe) { this.tieuDe = tieuDe; }
    public String getMoTa() { return moTa; }
    public void setMoTa(String moTa) { this.moTa = moTa; }
    public String getNguoiYeuCau() { return nguoiYeuCau; }
    public void setNguoiYeuCau(String nguoiYeuCau) { this.nguoiYeuCau = nguoiYeuCau; }
    public String getAnhSuCo() { return anhSuCo; }
    public void setAnhSuCo(String anhSuCo) { this.anhSuCo = anhSuCo; }
    public String getTrangThai() { return trangThai; }
    public void setTrangThai(String trangThai) { this.trangThai = trangThai; }
    public String getTrangThaiTruocBaoTri() { return trangThaiTruocBaoTri; }
    public void setTrangThaiTruocBaoTri(String trangThaiTruocBaoTri) { this.trangThaiTruocBaoTri = trangThaiTruocBaoTri; }
    public BigDecimal getChiPhi() { return chiPhi; }
    public void setChiPhi(BigDecimal chiPhi) { this.chiPhi = chiPhi; }
    public Instant getTaoLuc() { return taoLuc; }
    public Instant getHoanTatLuc() { return hoanTatLuc; }
    public void setHoanTatLuc(Instant hoanTatLuc) { this.hoanTatLuc = hoanTatLuc; }
    public String getNguoiThucHien() { return nguoiThucHien; }
    public void setNguoiThucHien(String nguoiThucHien) { this.nguoiThucHien = nguoiThucHien; }
    public LocalDate getNgayHoanThanh() { return ngayHoanThanh; }
    public void setNgayHoanThanh(LocalDate ngayHoanThanh) { this.ngayHoanThanh = ngayHoanThanh; }
    public LocalDate getNgayBaoHong() { return ngayBaoHong; }
    public void setNgayBaoHong(LocalDate ngayBaoHong) { this.ngayBaoHong = ngayBaoHong; }
    public String getMucDo() { return mucDo; }
    public void setMucDo(String mucDo) { this.mucDo = mucDo; }
    public String getNoiXuLy() { return noiXuLy; }
    public void setNoiXuLy(String noiXuLy) { this.noiXuLy = noiXuLy; }
    public String getKetQua() { return ketQua; }
    public void setKetQua(String ketQua) { this.ketQua = ketQua; }
    public String getLoaiYeuCau() { return loaiYeuCau; }
    public void setLoaiYeuCau(String loaiYeuCau) { this.loaiYeuCau = loaiYeuCau; }
    public String getNguonTao() { return nguonTao; }
    public void setNguonTao(String nguonTao) { this.nguonTao = nguonTao; }
    public String getPhongBan() { return phongBan; }
    public void setPhongBan(String phongBan) { this.phongBan = phongBan; }
}
