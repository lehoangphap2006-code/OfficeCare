package com.example.quanlytaisan.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record YeuCauBaoTriRequest(
        Integer taiSanId,
        String tieuDe,
        String moTa,
        String trangThai,
        BigDecimal chiPhi,
        String anhSuCo,
        String nguoiYeuCau,
        LocalDate ngayBaoHong,
        String mucDo,
        String noiXuLy,
        String loaiYeuCau,
        String nguonTao) {
    public YeuCauBaoTriRequest(Integer taiSanId, String tieuDe, String moTa, String trangThai,
            BigDecimal chiPhi, String anhSuCo) {
        this(taiSanId, tieuDe, moTa, trangThai, chiPhi, anhSuCo, null, null,
                null, null, null, null);
    }
}
