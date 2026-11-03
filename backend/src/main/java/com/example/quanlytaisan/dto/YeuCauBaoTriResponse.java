package com.example.quanlytaisan.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record YeuCauBaoTriResponse(
        Integer id,
        String maPhieu,
        Integer taiSanId,
        String maTaiSan,
        String tenTaiSan,
        String tieuDe,
        String moTa,
        String trangThai,
        BigDecimal chiPhi,
        Instant taoLuc,
        Instant hoanTatLuc,
        String nguoiYeuCau,
        String anhSuCo,
        String nguoiThucHien,
        LocalDate ngayHoanThanh,
        Integer lichBaoTriId,
        LocalDate ngayBaoHong,
        String mucDo,
        String noiXuLy,
        String ketQua,
        String loaiYeuCau,
        String nguonTao,
        String phongBan,
        String tenLoaiTaiSan) {
    public YeuCauBaoTriResponse(Integer id, Integer taiSanId, String maTaiSan, String tenTaiSan,
            String tieuDe, String moTa, String trangThai, BigDecimal chiPhi, Instant taoLuc,
            Instant hoanTatLuc, String nguoiYeuCau, String anhSuCo) {
        this(id, ticketNumber(id), taiSanId, maTaiSan, tenTaiSan, tieuDe, moTa, trangThai,
                chiPhi, taoLuc, hoanTatLuc, nguoiYeuCau, anhSuCo, null, null, null,
                null, null, null, null, null, null, null, null);
    }

    public static String ticketNumber(Integer id) {
        return id == null ? null : "SC-" + String.format("%06d", id);
    }
}
