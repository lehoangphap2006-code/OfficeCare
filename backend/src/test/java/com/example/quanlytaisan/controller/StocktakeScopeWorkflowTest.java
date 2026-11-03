package com.example.quanlytaisan.controller;

import com.example.quanlytaisan.entity.StocktakeSession;
import com.example.quanlytaisan.entity.StocktakeItem;
import com.example.quanlytaisan.entity.TaiSan;
import com.example.quanlytaisan.repository.*;
import com.example.quanlytaisan.service.ActivityLogService;
import com.example.quanlytaisan.service.MaintenanceScheduleService;
import com.example.quanlytaisan.service.StocktakePdfGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StocktakeScopeWorkflowTest {
    private AdminFoundationController controller;
    private StocktakeSessionRepository sessionRepository;
    private StocktakeItemRepository itemRepository;
    private TaiSanRepository assetRepository;
    private StocktakeExtraRepository extraRepository;
    private StocktakePdfGenerator pdfGenerator;
    private StocktakeSession session;
    private TaiSan inScope;

    @BeforeEach
    void setUp() {
        sessionRepository = mock(StocktakeSessionRepository.class);
        itemRepository = mock(StocktakeItemRepository.class);
        assetRepository = mock(TaiSanRepository.class);
        extraRepository = mock(StocktakeExtraRepository.class);
        pdfGenerator = mock(StocktakePdfGenerator.class);
        controller = new AdminFoundationController(mock(AdminMasterDataRepository.class),
                mock(AdminEmployeeRepository.class), mock(AppUserRepository.class), mock(ConsumableRepository.class),
                mock(ConsumableIssueRepository.class), mock(ConsumableReceiptRepository.class),
                mock(ConsumableRequestRepository.class), mock(AdminInvoiceRepository.class),
                mock(AdminInvoiceAttachmentRepository.class), mock(AdminInvoiceLineRepository.class),
                mock(AdminInvoicePaymentRepository.class), mock(AdminInvoiceAdjustmentRepository.class),
                sessionRepository, itemRepository, extraRepository, assetRepository,
                mock(TaiSanCapPhatRepository.class),
                mock(YeuCauBaoTriRepository.class), mock(ActivityLogService.class),
                pdfGenerator, mock(MaintenanceScheduleService.class),
                mock(ThuongMaiOrderRepository.class));
        session = new StocktakeSession();
        ReflectionTestUtils.setField(session, "id", 12);
        session.setName("Kiểm kê phòng họp");
        session.setStatus("DRAFT");
        session.setScopeType("LOCATION");
        session.setScopeValue("Tòa A · Tầng 2 · Phòng 201");
        when(sessionRepository.findByIdForUpdate(12)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(itemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(itemRepository.countBySession_Id(12)).thenReturn(1L);
        when(itemRepository.countBySession_IdAndCheckedAtIsNotNull(12)).thenReturn(0L);
        inScope = asset(1, "Tòa A", "2", "201");
    }

    @Test
    void startSnapshotsOnlyAssetsInSelectedLocation() {
        TaiSan elsewhere = asset(2, "Tòa B", "1", "101");
        when(assetRepository.findAll(any(Sort.class))).thenReturn(List.of(inScope, elsewhere));
        when(assetRepository.findAllByIdForUpdate(List.of(1))).thenReturn(List.of(inScope));
        when(itemRepository.findAssetIdsInOpenSessions(List.of(1))).thenReturn(List.of());
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("admin");

        var response = controller.startStocktake(12, auth);

        assertEquals("OPEN", response.trangThai());
        assertEquals("LOCATION", response.loaiPhamVi());
        assertEquals("Tòa A · Tầng 2 · Phòng 201", response.phamVi());
        verify(itemRepository).save(argThat(item -> item.getAsset().getTaiSanId().equals(1)));
        verify(itemRepository, times(1)).save(any());
    }

    @Test
    void overlappingOpenStocktakePreventsStartingAnotherSnapshot() {
        when(assetRepository.findAll(any(Sort.class))).thenReturn(List.of(inScope));
        when(assetRepository.findAllByIdForUpdate(List.of(1))).thenReturn(List.of(inScope));
        when(itemRepository.findAssetIdsInOpenSessions(List.of(1))).thenReturn(List.of(1));
        Authentication auth = mock(Authentication.class);

        assertThrows(ResponseStatusException.class, () -> controller.startStocktake(12, auth));

        verify(itemRepository, never()).save(any());
        assertEquals("DRAFT", session.getStatus());
    }

    @Test
    void completionExplainsHowManyItemsRemainUnchecked() {
        session.setStatus("OPEN");
        StocktakeItem checked = stocktakeItem(true);
        StocktakeItem unchecked = stocktakeItem(false);
        when(itemRepository.findBySession_IdOrderByIdAsc(12)).thenReturn(List.of(checked, unchecked));
        Authentication auth = mock(Authentication.class);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.completeStocktake(12, auth));

        assertEquals("Còn 1 tài sản chưa kiểm; không thể hoàn thành đợt", error.getReason());
        assertEquals("OPEN", session.getStatus());
        verify(sessionRepository, never()).save(session);
    }

    @Test
    void completesStocktakeAfterAllItemsAreChecked() {
        session.setStatus("OPEN");
        session.setCheckerName("Người kiểm");
        session.setConfirmerName("Người xác nhận");
        inScope.setTenTaiSan("Bàn phím 😀");
        StocktakeItem checked = stocktakeItem(true);
        checked.setNotes("Đã kiểm tra ✅");
        when(itemRepository.findBySession_IdOrderByIdAsc(12)).thenReturn(List.of(checked));
        when(extraRepository.findBySession_IdOrderByIdAsc(12)).thenReturn(List.of());
        when(itemRepository.countBySession_Id(12)).thenReturn(1L);
        when(itemRepository.countBySession_IdAndCheckedAtIsNotNull(12)).thenReturn(1L);
        ReflectionTestUtils.setField(controller, "stocktakePdfGenerator", new StocktakePdfGenerator());
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("admin");

        var response = controller.completeStocktake(12, auth);

        assertEquals("COMPLETED", response.trangThai());
        assertEquals("COMPLETED", session.getStatus());
        assertTrue(session.getReportPdf().length > 0);
        verify(sessionRepository).save(session);
    }

    private StocktakeItem stocktakeItem(boolean checked) {
        StocktakeItem item = new StocktakeItem();
        item.setSession(session);
        item.setAsset(inScope);
        item.setConditionStatus("CO_MAT");
        if (checked) item.setCheckedAt(Instant.now());
        return item;
    }

    private TaiSan asset(int id, String building, String floor, String room) {
        TaiSan asset = new TaiSan();
        ReflectionTestUtils.setField(asset, "taiSanId", id);
        asset.setMaTaiSan("ASSET-" + id);
        asset.setTenTaiSan("Thiết bị " + id);
        asset.setToaNha(building);
        asset.setTang(floor);
        asset.setPhong(room);
        return asset;
    }
}
