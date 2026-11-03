package com.example.quanlytaisan.repository;

import java.math.BigDecimal;
import java.time.Instant;

public interface BaoTriTongHop {
    Integer getTaiSanId();
    Instant getBaoTriGanNhat();
    BigDecimal getChiPhiBaoTriTichLuy();
}
