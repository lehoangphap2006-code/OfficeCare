package com.example.quanlytaisan.dto;

import java.time.LocalDate;

public record ThuHoiTaiSanRequest(
        LocalDate ngayThuHoi,
        String lyDoThuHoi,
        String tinhTrangLucThuHoi,
        String tinhTrangSauThuHoi,
        String ghiChu,
        String nguoiNhanLai,
        String nguoiGiaoLai) {
    public ThuHoiTaiSanRequest(String ghiChu, String tinhTrangLucThuHoi, String tinhTrangSauThuHoi) {
        this(null, null, tinhTrangLucThuHoi, tinhTrangSauThuHoi, ghiChu, null, null);
    }
}
