package com.example.quanlytaisan.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class StockReceiptDtos {
    private StockReceiptDtos() {}

    public record Response(
            Integer id,
            String soPhieu,
            Integer donMuaId,
            String maDonMua,
            String nhaCungCap,
            LocalDate ngayNhap,
            String nguoiNhap,
            String ghiChu,
            LocalDateTime taoLuc,
            List<Item> chiTiet) {
    }

    public record Item(
            Integer chiTietDonId,
            String tenSanPham,
            Integer soLuongTheoDon,
            Integer soLuongDaNhanTruoc,
            Integer soLuongThucNhan,
            Integer soLuongHong,
            Integer soLuongThieu,
            String ghiChu) {
    }
}
