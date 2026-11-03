package com.example.quanlytaisan.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "tai_san_thu_hoi_bien_ban")
public class TaiSanThuHoiBienBan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tai_san_cap_phat_id", nullable = false, unique = true)
    private TaiSanCapPhat phieuBanGiao;

    @Lob
    @Column(nullable = false)
    private byte[] noiDungPdf;

    public Integer getId() { return id; }
    public TaiSanCapPhat getPhieuBanGiao() { return phieuBanGiao; }
    public void setPhieuBanGiao(TaiSanCapPhat phieuBanGiao) { this.phieuBanGiao = phieuBanGiao; }
    public byte[] getNoiDungPdf() { return noiDungPdf; }
    public void setNoiDungPdf(byte[] noiDungPdf) { this.noiDungPdf = noiDungPdf; }
}
