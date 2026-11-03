package com.example.quanlytaisan.dto;

import java.math.BigDecimal;

public record TaiSanStatsResponse(
        long tongTaiSan,
        long dangSuDung,
        long trongKho,
        long baoTri,
        long choThanhLy,
        long thanhLy,
        BigDecimal tongGiaTri) {
}
