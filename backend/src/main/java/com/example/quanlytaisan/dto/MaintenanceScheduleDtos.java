package com.example.quanlytaisan.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class MaintenanceScheduleDtos {
    private MaintenanceScheduleDtos() {}

    public record Request(Integer taiSanId, Integer chuKyThang, LocalDate ngayBatDau) {}

    public record Response(Integer id, Integer taiSanId, String maTaiSan, String tenTaiSan,
            String tenLoaiTaiSan, String trangThaiTaiSan, String nguoiDangGiu, String phongBan,
            Integer chuKyThang, LocalDate ngayBatDau, LocalDate ngayBaoTriGanNhat,
            LocalDate ngayDenHanTiepTheo, String trangThai) {}

    public record CompleteRequest(String noiDung, String nguoiThucHien, BigDecimal chiPhi,
            LocalDate ngayHoanThanh) {}
}
