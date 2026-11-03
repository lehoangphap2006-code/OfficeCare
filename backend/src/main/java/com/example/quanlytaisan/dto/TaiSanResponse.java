package com.example.quanlytaisan.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record TaiSanResponse(
        Integer taiSanId,
        String maTaiSan,
        String tenTaiSan,
        BigDecimal giaMua,
        String tinhTrang,
        Integer loaiTaiSanId,
        String tenLoaiTaiSan,
        Integer chuKyBaoTriThang,
        Instant baoTriGanNhat,
        Instant baoTriTiepTheo,
        BigDecimal chiPhiBaoTriTichLuy,
        LocalDate ngayMua,
        String toaNha,
        String tang,
        String phong,
        Integer thoiGianKhauHaoNam,
        BigDecimal giaTriConLai,
        String nguoiDangGiu,
        String phongBanDangGiu,
        String serialNumber,
        String brand,
        String model,
        Integer supplierId,
        LocalDate warrantyEndDate,
        String imageReference,
        String notes,
        String supplierName) {
}
