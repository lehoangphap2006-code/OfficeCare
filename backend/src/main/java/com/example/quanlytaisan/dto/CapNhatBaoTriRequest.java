package com.example.quanlytaisan.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CapNhatBaoTriRequest(String trangThai, BigDecimal chiPhi, String ketQua,
        String nguoiThucHien, LocalDate ngayHoanThanh) {
    public CapNhatBaoTriRequest(String trangThai, BigDecimal chiPhi) {
        this(trangThai, chiPhi, null, null, null);
    }
}
