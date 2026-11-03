package com.example.quanlytaisan.controller;

import com.example.quanlytaisan.dto.AdminDtos.ConsumableReceiptRequest;
import com.example.quanlytaisan.dto.AdminDtos.IssueRequest;
import com.example.quanlytaisan.entity.AdminMasterData;
import com.example.quanlytaisan.entity.Consumable;
import com.example.quanlytaisan.repository.*;
import com.example.quanlytaisan.service.ActivityLogService;
import com.example.quanlytaisan.service.MaintenanceScheduleService;
import com.example.quanlytaisan.service.StocktakePdfGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.security.core.Authentication;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ConsumableInventoryWorkflowTest {
    private AdminFoundationController controller;
    private ConsumableRepository consumableRepository;
    private ConsumableIssueRepository issueRepository;
    private ConsumableReceiptRepository receiptRepository;
    private AdminMasterDataRepository masterRepository;
    private ConsumableRequestRepository requestRepository;
    private MaintenanceScheduleService scheduleService;
    private YeuCauBaoTriRepository maintenanceRepository;
    private Consumable item;
    private Authentication auth;

    @BeforeEach
    void setUp() {
        masterRepository = mock(AdminMasterDataRepository.class);
        consumableRepository = mock(ConsumableRepository.class);
        issueRepository = mock(ConsumableIssueRepository.class);
        receiptRepository = mock(ConsumableReceiptRepository.class);
        requestRepository = mock(ConsumableRequestRepository.class);
        scheduleService = mock(MaintenanceScheduleService.class);
        maintenanceRepository = mock(YeuCauBaoTriRepository.class);
        controller = new AdminFoundationController(masterRepository, mock(AdminEmployeeRepository.class),
                mock(AppUserRepository.class), consumableRepository, issueRepository,
                receiptRepository, requestRepository,
                mock(AdminInvoiceRepository.class), mock(AdminInvoiceAttachmentRepository.class),
                mock(AdminInvoiceLineRepository.class),
                mock(AdminInvoicePaymentRepository.class), mock(AdminInvoiceAdjustmentRepository.class),
                mock(StocktakeSessionRepository.class),
                mock(StocktakeItemRepository.class), mock(StocktakeExtraRepository.class),
                mock(TaiSanRepository.class),
                mock(TaiSanCapPhatRepository.class), maintenanceRepository,
                mock(ActivityLogService.class), mock(StocktakePdfGenerator.class),
                scheduleService, mock(ThuongMaiOrderRepository.class));
        item = new Consumable();
        ReflectionTestUtils.setField(item, "id", 2);
        item.setCode("PAPER-A4");
        item.setName("Giấy A4");
        item.setUnit("ream");
        item.setStockQuantity(BigDecimal.ZERO);
        item.setMinimumStock(new BigDecimal("2"));
        when(consumableRepository.findByIdForUpdate(2)).thenReturn(Optional.of(item));
        when(consumableRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(issueRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(receiptRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("admin");
    }

    @Test
    void receivingTenAndIssuingFourLeavesSixInStock() {
        AdminMasterData supplier = master(1, "SUPPLIER", "Nhà cung cấp");
        AdminMasterData department = master(10, "DEPARTMENT", "Hành chính");
        when(masterRepository.findById(1)).thenReturn(Optional.of(supplier));
        when(masterRepository.findById(10)).thenReturn(Optional.of(department));

        controller.receiveConsumable(new ConsumableReceiptRequest(2, BigDecimal.TEN,
                new BigDecimal("50000"), 1, LocalDate.now(), "PN-01", null), auth);
        controller.issueConsumable(new IssueRequest(2, 10, null, new BigDecimal("4"),
                null, "Nguyễn An", LocalDate.now()), auth);

        assertEquals(new BigDecimal("6"), item.getStockQuantity());
        verify(issueRepository).save(argThat(issue -> issue.getRecipient().equals("Nguyễn An")
                && issue.getQuantity().equals(new BigDecimal("4"))));
    }

    @Test
    void rejectsIssueWhenRequestedQuantityExceedsStock() {
        item.setStockQuantity(new BigDecimal("6"));
        when(masterRepository.findById(10)).thenReturn(Optional.of(master(10, "DEPARTMENT", "Hành chính")));

        assertThrows(ResponseStatusException.class, () -> controller.issueConsumable(
                new IssueRequest(2, 10, null, new BigDecimal("7"), null, "Nguyễn An", LocalDate.now()), auth));

        assertEquals(new BigDecimal("6"), item.getStockQuantity());
        verify(issueRepository, never()).save(any());
    }

    @Test
    void issuesConsumableWithoutDepartment() {
        item.setStockQuantity(new BigDecimal("6"));

        var response = controller.issueConsumable(
                new IssueRequest(2, null, null, new BigDecimal("4"), null, "Nguyễn An", LocalDate.now()), auth);

        assertNull(response.phongBan());
        assertNull(response.phongBanId());
        assertEquals(new BigDecimal("2"), item.getStockQuantity());
        verify(issueRepository).save(argThat(issue -> issue.getDepartmentId() == null
                && issue.getDepartmentName() == null
                && issue.getRecipient().equals("Nguyễn An")));
    }

    @Test
    void issuesFractionalQuantityWithoutDepartment() {
        item.setStockQuantity(new BigDecimal("0.09"));

        var response = controller.issueConsumable(
                new IssueRequest(2, null, null, new BigDecimal("0.01"), "đã xong", "lhp", LocalDate.now()), auth);

        assertNull(response.phongBan());
        assertEquals(new BigDecimal("0.08"), item.getStockQuantity());
        verify(issueRepository).save(argThat(issue -> issue.getQuantity().equals(new BigDecimal("0.01"))
                && issue.getRecipient().equals("lhp")));
    }

    @Test
    void acceptsTheSameFractionalIssuePayloadSentByTheAdminForm() throws Exception {
        item.setStockQuantity(new BigDecimal("0.09"));
        MockMvc mockMvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new AdminFoundationApiExceptionHandler())
                .build();

        mockMvc.perform(post("/api/admin/vat-tu/phieu-xuat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .principal(auth)
                        .content("""
                                {"vatTuId":2,"soLuong":0.09,"nguoiNhan":"lhp","ngayXuat":"2026-11-02","ghiChu":"đã xong"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.soLuong").value(0.09));

        assertEquals(new BigDecimal("0.00"), item.getStockQuantity());
    }

    @Test
    void dashboardWarnsForActiveConsumablesBelowMinimumButNotInactiveItems() {
        item.setStockQuantity(new BigDecimal("1"));
        Consumable stopped = new Consumable();
        stopped.setName("Vật tư đã ngừng");
        stopped.setStockQuantity(BigDecimal.ZERO);
        stopped.setMinimumStock(BigDecimal.TEN);
        stopped.setActive(false);
        when(consumableRepository.findAll()).thenReturn(java.util.List.of(item, stopped));
        when(scheduleService.schedules()).thenReturn(java.util.List.of());
        when(maintenanceRepository.totalRepairCost()).thenReturn(BigDecimal.ZERO);

        var dashboard = controller.dashboard(null, null, null, null, null);

        assertTrue(dashboard.canhBao().stream().anyMatch(alert -> alert.loai().equals("LOW_STOCK")
                && alert.tieuDe().contains("Giấy A4")));
        assertFalse(dashboard.canhBao().stream().anyMatch(alert -> alert.tieuDe().contains("Vật tư đã ngừng")));
    }

    private AdminMasterData master(int id, String kind, String name) {
        AdminMasterData row = new AdminMasterData();
        ReflectionTestUtils.setField(row, "id", id);
        row.setKind(kind);
        row.setName(name);
        row.setCode(name);
        return row;
    }
}
