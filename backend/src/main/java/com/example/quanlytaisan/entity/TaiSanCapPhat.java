package com.example.quanlytaisan.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "tai_san_cap_phat")
public class TaiSanCapPhat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tai_san_id", nullable = false)
    private TaiSan taiSan;

    @Column(nullable = false, length = 100)
    private String nguoiNhan;

    @Column(length = 50)
    private String usernameNguoiNhan;

    @Column(length = 100)
    private String phongBan;

    @Column(nullable = false)
    private Instant banGiaoLuc = Instant.now();

    @Column(length = 100)
    private String nguoiBanGiao;

    @Column(length = 100)
    private String nguoiKyNhan;

    private Instant thuHoiLuc;

    private LocalDate ngayDuKienThuHoi;

    @Column(length = 500)
    private String ghiChu;

    @Column(length = 300)
    private String tinhTrangLucBanGiao;

    @Column(length = 500)
    private String ghiChuThuHoi;

    @Column(length = 300)
    private String tinhTrangLucThuHoi;

    @Column(length = 30)
    private String tinhTrangSauThuHoi;

    @Column(length = 30)
    private String lyDoThuHoi;

    @Column(length = 100)
    private String nguoiNhanLai;

    @Column(length = 100)
    private String nguoiGiaoLai;

    @Column(nullable = false)
    private boolean coBienBanThuHoi;

    public Integer getId() { return id; }
    public TaiSan getTaiSan() { return taiSan; }
    public void setTaiSan(TaiSan taiSan) { this.taiSan = taiSan; }
    public String getNguoiNhan() { return nguoiNhan; }
    public void setNguoiNhan(String nguoiNhan) { this.nguoiNhan = nguoiNhan; }
    public String getUsernameNguoiNhan() { return usernameNguoiNhan; }
    public void setUsernameNguoiNhan(String usernameNguoiNhan) { this.usernameNguoiNhan = usernameNguoiNhan; }
    public String getPhongBan() { return phongBan; }
    public void setPhongBan(String phongBan) { this.phongBan = phongBan; }
    public Instant getBanGiaoLuc() { return banGiaoLuc; }
    public void setBanGiaoLuc(Instant banGiaoLuc) { this.banGiaoLuc = banGiaoLuc; }
    public String getNguoiBanGiao() { return nguoiBanGiao; }
    public void setNguoiBanGiao(String nguoiBanGiao) { this.nguoiBanGiao = nguoiBanGiao; }
    public String getNguoiKyNhan() { return nguoiKyNhan; }
    public void setNguoiKyNhan(String nguoiKyNhan) { this.nguoiKyNhan = nguoiKyNhan; }
    public Instant getThuHoiLuc() { return thuHoiLuc; }
    public void setThuHoiLuc(Instant thuHoiLuc) { this.thuHoiLuc = thuHoiLuc; }
    public LocalDate getNgayDuKienThuHoi() { return ngayDuKienThuHoi; }
    public void setNgayDuKienThuHoi(LocalDate ngayDuKienThuHoi) { this.ngayDuKienThuHoi = ngayDuKienThuHoi; }
    public String getGhiChu() { return ghiChu; }
    public void setGhiChu(String ghiChu) { this.ghiChu = ghiChu; }
    public String getTinhTrangLucBanGiao() { return tinhTrangLucBanGiao; }
    public void setTinhTrangLucBanGiao(String tinhTrangLucBanGiao) { this.tinhTrangLucBanGiao = tinhTrangLucBanGiao; }
    public String getGhiChuThuHoi() { return ghiChuThuHoi; }
    public void setGhiChuThuHoi(String ghiChuThuHoi) { this.ghiChuThuHoi = ghiChuThuHoi; }
    public String getTinhTrangLucThuHoi() { return tinhTrangLucThuHoi; }
    public void setTinhTrangLucThuHoi(String tinhTrangLucThuHoi) { this.tinhTrangLucThuHoi = tinhTrangLucThuHoi; }
    public String getTinhTrangSauThuHoi() { return tinhTrangSauThuHoi; }
    public void setTinhTrangSauThuHoi(String tinhTrangSauThuHoi) { this.tinhTrangSauThuHoi = tinhTrangSauThuHoi; }
    public String getLyDoThuHoi() { return lyDoThuHoi; }
    public void setLyDoThuHoi(String lyDoThuHoi) { this.lyDoThuHoi = lyDoThuHoi; }
    public String getNguoiNhanLai() { return nguoiNhanLai; }
    public void setNguoiNhanLai(String nguoiNhanLai) { this.nguoiNhanLai = nguoiNhanLai; }
    public String getNguoiGiaoLai() { return nguoiGiaoLai; }
    public void setNguoiGiaoLai(String nguoiGiaoLai) { this.nguoiGiaoLai = nguoiGiaoLai; }
    public boolean isCoBienBanThuHoi() { return coBienBanThuHoi; }
    public void setCoBienBanThuHoi(boolean coBienBanThuHoi) { this.coBienBanThuHoi = coBienBanThuHoi; }
}
