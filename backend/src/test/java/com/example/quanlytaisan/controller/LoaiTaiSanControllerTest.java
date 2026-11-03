package com.example.quanlytaisan.controller;

import com.example.quanlytaisan.entity.LoaiTaiSan;
import com.example.quanlytaisan.entity.PurchaseRequest;
import com.example.quanlytaisan.entity.TaiSan;
import com.example.quanlytaisan.repository.LoaiTaiSanRepository;
import com.example.quanlytaisan.repository.PurchaseRequestRepository;
import com.example.quanlytaisan.repository.TaiSanRepository;
import com.example.quanlytaisan.service.ActivityLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

class LoaiTaiSanControllerTest {
    private LoaiTaiSanRepository categoryRepository;
    private TaiSanRepository assetRepository;
    private PurchaseRequestRepository requestRepository;
    private ActivityLogService activityLog;
    private Authentication authentication;
    private LoaiTaiSanController controller;

    @BeforeEach
    void setUp() {
        categoryRepository = mock(LoaiTaiSanRepository.class);
        assetRepository = mock(TaiSanRepository.class);
        requestRepository = mock(PurchaseRequestRepository.class);
        activityLog = mock(ActivityLogService.class);
        authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("admin");
        controller = new LoaiTaiSanController(categoryRepository, assetRepository, requestRepository, activityLog);
    }

    @Test
    void deletesCategoryAndKeepsAssetsWithoutCategoryWhenNoReplacementIsNeeded() {
        LoaiTaiSan category = category(4, "Thiết bị trình chiếu");
        TaiSan asset = new TaiSan();
        asset.setLoaiTaiSan(category);
        when(categoryRepository.existsById(4)).thenReturn(true);
        when(categoryRepository.findById(4)).thenReturn(Optional.of(category));
        when(assetRepository.countByLoaiTaiSan_LoaiId(4)).thenReturn(1L);
        when(requestRepository.countByCategoryId(4)).thenReturn(0L);
        when(assetRepository.findByLoaiTaiSan_LoaiId(4)).thenReturn(List.of(asset));

        controller.xoa(4, null, authentication);

        assertNull(asset.getLoaiTaiSan());
        verify(assetRepository).saveAll(List.of(asset));
        verify(categoryRepository).deleteById(4);
    }

    @Test
    void reassignsAssetsAndPurchaseRequestsBeforeDeletingCategory() {
        LoaiTaiSan category = category(4, "Thiết bị trình chiếu");
        LoaiTaiSan replacement = category(9, "Máy tính & laptop");
        TaiSan asset = new TaiSan();
        asset.setLoaiTaiSan(category);
        PurchaseRequest request = new PurchaseRequest();
        request.setCategoryId(4);
        when(categoryRepository.existsById(4)).thenReturn(true);
        when(categoryRepository.findById(4)).thenReturn(Optional.of(category));
        when(categoryRepository.findById(9)).thenReturn(Optional.of(replacement));
        when(assetRepository.countByLoaiTaiSan_LoaiId(4)).thenReturn(1L);
        when(requestRepository.countByCategoryId(4)).thenReturn(1L);
        when(assetRepository.findByLoaiTaiSan_LoaiId(4)).thenReturn(List.of(asset));
        when(requestRepository.findByCategoryId(4)).thenReturn(List.of(request));

        controller.xoa(4, 9, authentication);

        assertEquals(replacement, asset.getLoaiTaiSan());
        assertEquals(9, request.getCategoryId());
        verify(assetRepository).saveAll(List.of(asset));
        verify(requestRepository).saveAll(List.of(request));
        verify(categoryRepository).deleteById(4);
    }

    @Test
    void requiresReplacementWhenPurchaseRequestsUseCategory() {
        LoaiTaiSan category = category(4, "Thiết bị trình chiếu");
        when(categoryRepository.existsById(4)).thenReturn(true);
        when(categoryRepository.findById(4)).thenReturn(Optional.of(category));
        when(assetRepository.countByLoaiTaiSan_LoaiId(4)).thenReturn(0L);
        when(requestRepository.countByCategoryId(4)).thenReturn(1L);

        assertThrows(ResponseStatusException.class, () -> controller.xoa(4, null, authentication));
        verify(categoryRepository, never()).deleteById(4);
    }

    private LoaiTaiSan category(Integer id, String name) {
        LoaiTaiSan category = new LoaiTaiSan();
        category.setLoaiId(id);
        category.setTenLoai(name);
        return category;
    }
}
