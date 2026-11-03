package com.example.quanlytaisan.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ThuongMaiOrderResponse(
        Integer id,
        String maDonHang,
        String loaiDon,
        String trangThai,
        String phuongThucThanhToan,
        String trangThaiThanhToan,
        BigDecimal tongTien,
        Instant taoLuc,
        String maGiaoDich,
        Integer nhaCungCapId,
        String nhaCungCap,
        String lyDoThanhLy,
        String ghiChuThanhLy,
        String phuongThucNhanTien,
        LocalDate ngayThanhLy,
        LocalDate ngayDuKienGiao,
        String nguoiYeuCau,
        String nguoiDuyet,
        Instant duyetLuc,
        Instant nhanHangLuc,
        BigDecimal laiLo,
        boolean coBienBanThanhLy,
        String ghiChuDuyetThanhLy,
        List<Item> chiTiet) {
    public ThuongMaiOrderResponse(
            Integer id,
            String maDonHang,
            String loaiDon,
            String trangThai,
            String phuongThucThanhToan,
            String trangThaiThanhToan,
            BigDecimal tongTien,
            Instant taoLuc,
            String maGiaoDich,
            Integer nhaCungCapId,
            String nhaCungCap,
            String lyDoThanhLy,
            String ghiChuThanhLy,
            String phuongThucNhanTien,
            LocalDate ngayThanhLy,
            LocalDate ngayDuKienGiao,
            String nguoiYeuCau,
            String nguoiDuyet,
            Instant duyetLuc,
            Instant nhanHangLuc,
            BigDecimal laiLo,
            boolean coBienBanThanhLy,
            List<Item> chiTiet) {
        this(id, maDonHang, loaiDon, trangThai, phuongThucThanhToan, trangThaiThanhToan, tongTien,
                taoLuc, maGiaoDich, nhaCungCapId, nhaCungCap, lyDoThanhLy, ghiChuThanhLy,
                phuongThucNhanTien, ngayThanhLy, ngayDuKienGiao, nguoiYeuCau, nguoiDuyet,
                duyetLuc, nhanHangLuc, laiLo, coBienBanThanhLy, null, chiTiet);
    }

    public record Item(
            Integer id,
            Integer taiSanId,
            String maTaiSan,
            String tenSanPham,
            Integer soLuong,
            Integer soLuongDaNhan,
            BigDecimal donGia,
            BigDecimal giaTriConLai,
            BigDecimal thanhTien,
            String toaNha,
            String tang,
            String phong,
            String loaiDong,
            Integer vatTuId,
            Integer loaiTaiSanId,
            LocalDate warrantyEndDate) {
    }
}
