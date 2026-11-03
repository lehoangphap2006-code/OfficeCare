package com.example.quanlytaisan.dto;

public record LoaiTaiSanResponse(Integer loaiId, String tenLoai, long soTaiSan,
        Integer thoiGianKhauHaoNam, boolean requiresSerial, long soYeuCauMuaSam) {
    public LoaiTaiSanResponse(Integer loaiId, String tenLoai, long soTaiSan,
            Integer thoiGianKhauHaoNam, boolean requiresSerial) {
        this(loaiId, tenLoai, soTaiSan, thoiGianKhauHaoNam, requiresSerial, 0);
    }

    public LoaiTaiSanResponse(Integer loaiId, String tenLoai, long soTaiSan, Integer thoiGianKhauHaoNam) {
        this(loaiId, tenLoai, soTaiSan, thoiGianKhauHaoNam, false, 0);
    }
}
