package com.example.quanlytaisan.service;

import com.example.quanlytaisan.entity.StocktakeItem;
import com.example.quanlytaisan.entity.StocktakeSession;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StocktakePdfGeneratorTest {
    @Test
    void generatesReportForCompletedStocktakeWithVietnameseAndUnsupportedAssetText() {
        StocktakeSession session = new StocktakeSession();
        ReflectionTestUtils.setField(session, "id", 2);
        session.setName("Kiểm kê phòng 42 😀");
        session.setStocktakeDate(LocalDate.of(2026, 11, 2));
        session.setEndDate(LocalDate.of(2026, 11, 3));
        session.setScopeType("LOCATION");
        session.setScopeValue("Vị trí 4 · Tầng 3 · Phòng 42");
        session.setResponsibleUsername("admin");
        session.setCheckerName("admin");
        session.setConfirmerName("Người xác nhận");

        com.example.quanlytaisan.entity.TaiSan asset = new com.example.quanlytaisan.entity.TaiSan();
        asset.setMaTaiSan("DH00000005-0010");
        asset.setTenTaiSan("Bàn phím 😀");
        StocktakeItem item = new StocktakeItem();
        item.setAsset(asset);
        item.setConditionStatus("CO_MAT");
        item.setBookLocation("Vị trí 4 · Tầng 3 · Phòng 42");
        item.setActualLocation("Vị trí 4 · Tầng 3 · Phòng 42");
        item.setNotes("Đã kiểm tra ✅");
        item.setCheckedAt(Instant.now());

        byte[] report = new StocktakePdfGenerator().generate(session, List.of(item), List.of());

        assertNotNull(report);
        assertTrue(report.length > 0);
        assertEquals('%', report[0]);
        assertEquals('P', report[1]);
        assertEquals('D', report[2]);
        assertEquals('F', report[3]);
    }
}
