package com.example.quanlytaisan.dto;

import java.time.Instant;

public record TaiSanCapPhatResponse(
        Integer id,
        Integer taiSanId,
        String maTaiSan,
        String tenTaiSan,
        String nguoiNhan,
        String phongBan,
        Instant banGiaoLuc,
        Instant thuHoiLuc,
        String ghiChu,
        String ghiChuThuHoi,
        String tinhTrangLucBanGiao,
        String tinhTrangLucThuHoi,
        String tinhTrangSauThuHoi,
        java.time.LocalDate ngayDuKienThuHoi,
        String usernameNguoiNhan,
        String nguoiBanGiao,
        String nguoiKyNhan,
        String serialNumber,
        String tenLoaiTaiSan,
        String toaNha,
        String tang,
        String phong,
        String lyDoThuHoi,
        String nguoiNhanLai,
        String nguoiGiaoLai,
        boolean coBienBanThuHoi) {
}
