package com.example.quanlytaisan.dto;

public record LoaiTaiSanRequest(String tenLoai, Integer thoiGianKhauHaoNam, Boolean requiresSerial) {
    public LoaiTaiSanRequest(String tenLoai, Integer thoiGianKhauHaoNam) {
        this(tenLoai, thoiGianKhauHaoNam, null);
    }
}
