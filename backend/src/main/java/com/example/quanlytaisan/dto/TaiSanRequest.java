package com.example.quanlytaisan.dto;

import java.math.BigDecimal;

public record TaiSanRequest(
        String maTaiSan,
        String tenTaiSan,
        BigDecimal giaMua,
        String tinhTrang,
        Integer loaiTaiSanId,
        Integer chuKyBaoTriThang,
        java.time.LocalDate ngayMua,
        String toaNha,
        String tang,
        String phong,
        String serialNumber,
        String brand,
        String model,
        Integer supplierId,
        java.time.LocalDate warrantyEndDate,
        String imageReference,
        String notes) {
}
