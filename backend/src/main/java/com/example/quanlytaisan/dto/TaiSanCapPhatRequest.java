package com.example.quanlytaisan.dto;

public record TaiSanCapPhatRequest(
        Integer taiSanId,
        String nguoiNhan,
        String phongBan,
        String ghiChu,
        String tinhTrangLucBanGiao,
        java.time.LocalDate ngayDuKienThuHoi,
        String usernameNguoiNhan,
        java.time.LocalDate ngayBanGiao,
        String nguoiBanGiao,
        String nguoiKyNhan) {
}
