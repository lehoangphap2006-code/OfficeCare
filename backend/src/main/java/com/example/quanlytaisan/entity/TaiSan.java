package com.example.quanlytaisan.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "tai_san")
public class TaiSan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer taiSanId;

    @Column(nullable = false, unique = true, length = 30)
    private String maTaiSan;

    @Column(nullable = false, length = 150)
    private String tenTaiSan;

    private BigDecimal giaMua;

    private LocalDate ngayMua;

    @Column(length = 100)
    private String toaNha;

    @Column(length = 30)
    private String tang;

    @Column(length = 100)
    private String phong;

    @Column(length = 100)
    private String serialNumber;

    @Column(length = 100)
    private String brand;

    @Column(length = 100)
    private String model;

    private Integer supplierId;

    private LocalDate warrantyEndDate;

    private Integer thoiGianKhauHaoNam;

    @Column(length = 500)
    private String imageReference;

    @Column(length = 2000)
    private String notes;

    private Integer chuKyBaoTriThang = 12;

    @Column(nullable = false, length = 30)
    private String tinhTrang = "Trong kho";

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "loai_id")
    private LoaiTaiSan loaiTaiSan;

    public Integer getTaiSanId() { return taiSanId; }
    public void setTaiSanId(Integer taiSanId) { this.taiSanId = taiSanId; }
    public String getMaTaiSan() { return maTaiSan; }
    public void setMaTaiSan(String maTaiSan) { this.maTaiSan = maTaiSan; }
    public String getTenTaiSan() { return tenTaiSan; }
    public void setTenTaiSan(String tenTaiSan) { this.tenTaiSan = tenTaiSan; }
    public BigDecimal getGiaMua() { return giaMua; }
    public void setGiaMua(BigDecimal giaMua) { this.giaMua = giaMua; }
    public LocalDate getNgayMua() { return ngayMua; }
    public void setNgayMua(LocalDate ngayMua) { this.ngayMua = ngayMua; }
    public String getToaNha() { return toaNha; }
    public void setToaNha(String toaNha) { this.toaNha = toaNha; }
    public String getTang() { return tang; }
    public void setTang(String tang) { this.tang = tang; }
    public String getPhong() { return phong; }
    public void setPhong(String phong) { this.phong = phong; }
    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public Integer getSupplierId() { return supplierId; }
    public void setSupplierId(Integer supplierId) { this.supplierId = supplierId; }
    public LocalDate getWarrantyEndDate() { return warrantyEndDate; }
    public void setWarrantyEndDate(LocalDate warrantyEndDate) { this.warrantyEndDate = warrantyEndDate; }
    public Integer getThoiGianKhauHaoNam() { return thoiGianKhauHaoNam; }
    public void setThoiGianKhauHaoNam(Integer thoiGianKhauHaoNam) { this.thoiGianKhauHaoNam = thoiGianKhauHaoNam; }
    public String getImageReference() { return imageReference; }
    public void setImageReference(String imageReference) { this.imageReference = imageReference; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Integer getChuKyBaoTriThang() { return chuKyBaoTriThang == null ? 12 : chuKyBaoTriThang; }
    public void setChuKyBaoTriThang(Integer chuKyBaoTriThang) { this.chuKyBaoTriThang = chuKyBaoTriThang; }
    public String getTinhTrang() { return tinhTrang; }
    public void setTinhTrang(String tinhTrang) { this.tinhTrang = tinhTrang; }
    public LoaiTaiSan getLoaiTaiSan() { return loaiTaiSan; }
    public void setLoaiTaiSan(LoaiTaiSan loaiTaiSan) { this.loaiTaiSan = loaiTaiSan; }
}
