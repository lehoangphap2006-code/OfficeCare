package com.example.quanlytaisan.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ThuongMaiOrderRequest(
        String loaiDon,
        String phuongThucThanhToan,
        Integer nhaCungCapId,
        LocalDate ngayDuKienGiao,
        String lyDoThanhLy,
        String phuongThucNhanTien,
        List<Item> chiTiet,
        String ghiChuThanhLy,
        LocalDate ngayThanhLy,
        String requestId) {
    public ThuongMaiOrderRequest(String loaiDon, String phuongThucThanhToan,
            Integer nhaCungCapId, LocalDate ngayDuKienGiao, String lyDoThanhLy,
            String phuongThucNhanTien, List<Item> chiTiet, String ghiChuThanhLy, LocalDate ngayThanhLy) {
        this(loaiDon, phuongThucThanhToan, nhaCungCapId, ngayDuKienGiao,
                lyDoThanhLy, phuongThucNhanTien, chiTiet, ghiChuThanhLy, ngayThanhLy, null);
    }

    public ThuongMaiOrderRequest(String loaiDon, String phuongThucThanhToan,
            Integer nhaCungCapId, LocalDate ngayDuKienGiao, String lyDoThanhLy,
            String phuongThucNhanTien, List<Item> chiTiet) {
        this(loaiDon, phuongThucThanhToan, nhaCungCapId, ngayDuKienGiao,
                lyDoThanhLy, phuongThucNhanTien, chiTiet, null, null, null);
    }

    public ThuongMaiOrderRequest(String loaiDon, String phuongThucThanhToan,
            Integer nhaCungCapId, LocalDate ngayDuKienGiao, String lyDoThanhLy,
            String phuongThucNhanTien, List<Item> chiTiet, String ghiChuThanhLy) {
        this(loaiDon, phuongThucThanhToan, nhaCungCapId, ngayDuKienGiao,
                lyDoThanhLy, phuongThucNhanTien, chiTiet, ghiChuThanhLy, null, null);
    }

    public record Item(
            Integer taiSanId,
            Integer loaiTaiSanId,
            Integer viTriId,
            String tenSanPham,
            Integer soLuong,
            BigDecimal donGia,
            String toaNha,
            String tang,
            String phong,
            String loaiDong,
            Integer vatTuId,
            LocalDate warrantyEndDate) {
    }
}
