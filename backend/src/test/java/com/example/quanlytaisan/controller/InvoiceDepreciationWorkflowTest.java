package com.example.quanlytaisan.controller;

import com.example.quanlytaisan.dto.AdminDtos.InvoiceLineRequest;
import com.example.quanlytaisan.dto.AdminDtos.InvoicePaymentRequest;
import com.example.quanlytaisan.dto.AdminDtos.InvoiceRequest;
import com.example.quanlytaisan.dto.AdminDtos.OrderInvoiceRequest;
import com.example.quanlytaisan.entity.AdminInvoiceLine;
import com.example.quanlytaisan.entity.AdminInvoice;
import com.example.quanlytaisan.entity.AdminInvoicePayment;
import com.example.quanlytaisan.entity.LoaiTaiSan;
import com.example.quanlytaisan.entity.TaiSan;
import com.example.quanlytaisan.entity.ThuongMaiOrder;
import com.example.quanlytaisan.entity.ThuongMaiOrderItem;
import com.example.quanlytaisan.repository.*;
import com.example.quanlytaisan.service.ActivityLogService;
import com.example.quanlytaisan.service.MaintenanceScheduleService;
import com.example.quanlytaisan.service.StocktakePdfGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class InvoiceDepreciationWorkflowTest {
    private AdminFoundationController controller;
    private AdminInvoiceRepository invoiceRepository;
    private AdminInvoicePaymentRepository paymentRepository;
    private AdminInvoiceLineRepository lineRepository;
    private AdminInvoiceAdjustmentRepository adjustmentRepository;
    private TaiSanRepository assetRepository;
    private ThuongMaiOrderRepository orderRepository;
    private Authentication auth;
    private AdminInvoice invoice;
    private final List<AdminInvoicePayment> payments = new ArrayList<>();

    @BeforeEach
    void setUp() {
        invoiceRepository = mock(AdminInvoiceRepository.class);
        paymentRepository = mock(AdminInvoicePaymentRepository.class);
        lineRepository = mock(AdminInvoiceLineRepository.class);
        adjustmentRepository = mock(AdminInvoiceAdjustmentRepository.class);
        assetRepository = mock(TaiSanRepository.class);
        orderRepository = mock(ThuongMaiOrderRepository.class);
        controller = new AdminFoundationController(mock(AdminMasterDataRepository.class),
                mock(AdminEmployeeRepository.class), mock(AppUserRepository.class), mock(ConsumableRepository.class),
                mock(ConsumableIssueRepository.class), mock(ConsumableReceiptRepository.class),
                mock(ConsumableRequestRepository.class), invoiceRepository,
                mock(AdminInvoiceAttachmentRepository.class), lineRepository, paymentRepository,
                adjustmentRepository, mock(StocktakeSessionRepository.class), mock(StocktakeItemRepository.class),
                mock(StocktakeExtraRepository.class), assetRepository,
                mock(TaiSanCapPhatRepository.class), mock(YeuCauBaoTriRepository.class),
                mock(ActivityLogService.class), mock(StocktakePdfGenerator.class),
                mock(MaintenanceScheduleService.class), orderRepository);
        invoice = new AdminInvoice();
        ReflectionTestUtils.setField(invoice, "id", 7);
        invoice.setInvoiceNumber("INV-7");
        invoice.setInvoiceDate(LocalDate.now());
        invoice.setTotalAmount(new BigDecimal("100.00"));
        invoice.setSupplierName("Nhà cung cấp");
        invoice.setPaymentStatus("CHUA_TRA");
        payments.clear();
        when(invoiceRepository.findByIdForUpdate(7)).thenReturn(Optional.of(invoice));
        when(invoiceRepository.save(any())).thenAnswer(invocation -> {
            AdminInvoice saved = invocation.getArgument(0);
            if (saved.getId() == null) ReflectionTestUtils.setField(saved, "id", 100);
            return saved;
        });
        when(lineRepository.findByInvoice_IdOrderByIdAsc(any())).thenReturn(List.of());
        when(paymentRepository.findByInvoice_IdOrderByPaymentDateAscIdAsc(any())).thenReturn(List.of());
        when(adjustmentRepository.findByInvoice_IdOrderByAdjustmentDateAscIdAsc(any())).thenReturn(List.of());
        when(lineRepository.findByInvoice_IdOrderByIdAsc(7)).thenReturn(List.of());
        when(paymentRepository.findByInvoice_IdOrderByPaymentDateAscIdAsc(7)).thenAnswer(invocation -> payments);
        when(adjustmentRepository.findByInvoice_IdOrderByAdjustmentDateAscIdAsc(7)).thenReturn(List.of());
        when(paymentRepository.save(any())).thenAnswer(invocation -> {
            AdminInvoicePayment saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", payments.size() + 1);
            payments.add(saved);
            return saved;
        });
        auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("admin");
    }

    @Test
    void rejectsPaymentThatExceedsInvoiceBalance() {
        assertThrows(ResponseStatusException.class, () -> controller.recordInvoicePayment(7,
                new InvoicePaymentRequest(LocalDate.now(), new BigDecimal("100.01"), "Chuyển khoản"), auth));
        verify(paymentRepository, never()).save(any());
        assertEquals("CHUA_TRA", invoice.getPaymentStatus());
    }

    @Test
    void fullPaymentMarksInvoiceAsPaid() {
        var response = controller.recordInvoicePayment(7,
                new InvoicePaymentRequest(LocalDate.now(), new BigDecimal("100.00"), "Chuyển khoản"), auth);
        assertEquals("DA_TRA", response.trangThaiThanhToan());
        assertEquals(BigDecimal.ZERO.setScale(2), response.conNo());
        assertEquals(1, response.thanhToan().size());
    }

    @Test
    void manualInvoiceTotalComesFromItsLineItems() {
        when(invoiceRepository.existsByInvoiceNumberIgnoreCase("INV-NEW")).thenReturn(false);
        var response = controller.createInvoice(new InvoiceRequest("INV-NEW", LocalDate.now(), null,
                null, null, null, "Mua thiết bị", List.of(
                        new InvoiceLineRequest("KHAC", "Laptop", new BigDecimal("3"),
                                new BigDecimal("4000000.00"), null, null))), auth);

        assertEquals(new BigDecimal("12000000.00"), response.tongTien());
        assertEquals("CHUA_TRA", response.trangThaiThanhToan());
        verify(invoiceRepository).save(argThat(saved -> saved.getInvoiceLines().size() == 1
                && saved.getInvoiceLines().get(0).getItemName().equals("Laptop")));
    }

    @Test
    void purchaseOrderInvoiceOnlyIncludesReceivedQuantityNotPreviouslyInvoiced() {
        ThuongMaiOrder order = new ThuongMaiOrder();
        order.setLoaiDon("MUA");
        order.setTrangThai("NHAN_MOT_PHAN");
        order.setSupplierName("Nhà cung cấp");
        ThuongMaiOrderItem orderItem = new ThuongMaiOrderItem();
        orderItem.setId(200);
        orderItem.setTenSanPham("Laptop");
        orderItem.setLoaiDong("TAI_SAN");
        orderItem.setSoLuong(10);
        orderItem.setSoLuongDaNhan(4);
        orderItem.setDonGia(new BigDecimal("10.00"));
        order.setChiTiet(List.of(orderItem));
        when(orderRepository.findByIdForUpdate(55)).thenReturn(Optional.of(order));

        AdminInvoice previousInvoice = new AdminInvoice();
        ReflectionTestUtils.setField(previousInvoice, "id", 21);
        when(invoiceRepository.findByPurchaseOrderId(55)).thenReturn(List.of(previousInvoice));
        AdminInvoiceLine previousLine = new AdminInvoiceLine();
        previousLine.setPurchaseOrderItemId(200);
        previousLine.setQuantity(new BigDecimal("2"));
        when(lineRepository.findByInvoice_IdOrderByIdAsc(21)).thenReturn(List.of(previousLine));
        when(invoiceRepository.existsByInvoiceNumberIgnoreCase("INV-PART-2")).thenReturn(false);

        var response = controller.createInvoiceFromPurchaseOrder(55,
                new OrderInvoiceRequest("INV-PART-2", LocalDate.now(), null), auth);

        assertEquals(new BigDecimal("20.00"), response.tongTien());
        verify(invoiceRepository).save(argThat(saved -> saved.getInvoiceLines().size() == 1
                && saved.getInvoiceLines().get(0).getQuantity().compareTo(new BigDecimal("2")) == 0));
    }

    @Test
    void straightLineDepreciationOfTwelveMillionOverThreeYearsIsFourMillionPerYear() {
        TaiSan asset = asset(2022, "Đang sử dụng");
        when(assetRepository.findAll()).thenReturn(List.of(asset));
        when(orderRepository.findAll()).thenReturn(List.of());

        var rows = controller.depreciation(null);

        assertEquals(3, rows.size());
        assertEquals(new BigDecimal("4000000.00"), rows.get(0).khauHaoNam());
        assertEquals(new BigDecimal("4000000.00"), rows.get(1).khauHaoNam());
        assertEquals(new BigDecimal("4000000.00"), rows.get(2).khauHaoNam());
        assertEquals(BigDecimal.ZERO.setScale(2), rows.get(2).giaTriConLaiCuoiNam());
    }

    @Test
    void depreciationStopsAfterDisposalYear() {
        TaiSan asset = asset(LocalDate.now().getYear() - 3, "Đã thanh lý");
        LocalDate disposed = asset.getNgayMua().plusYears(1).plusMonths(3);
        ThuongMaiOrder order = new ThuongMaiOrder();
        order.setLoaiDon("BAN");
        order.setTrangThai("DA_THANH_LY");
        order.setNgayThanhLy(disposed);
        ThuongMaiOrderItem item = new ThuongMaiOrderItem();
        item.setTaiSanId(asset.getTaiSanId());
        order.setChiTiet(List.of(item));
        when(assetRepository.findAll()).thenReturn(List.of(asset));
        when(orderRepository.findAll()).thenReturn(List.of(order));

        var rows = controller.depreciation(null);

        assertEquals(2, rows.size());
        assertTrue(rows.stream().allMatch(row -> row.nam() <= disposed.getYear()));
    }

    @Test
    void assetSpecificUsefulLifeOverridesCategoryDefault() {
        TaiSan asset = asset(LocalDate.now().getYear() - 2, "Đang sử dụng");
        asset.setThoiGianKhauHaoNam(2);
        when(assetRepository.findAll()).thenReturn(List.of(asset));
        when(orderRepository.findAll()).thenReturn(List.of());

        var rows = controller.depreciation(null);

        assertEquals(2, rows.size());
        assertEquals(new BigDecimal("6000000.00"), rows.get(0).khauHaoNam());
        assertEquals(BigDecimal.ZERO.setScale(2), rows.get(1).giaTriConLaiCuoiNam());
    }

    private TaiSan asset(int purchaseYear, String status) {
        return asset(LocalDate.of(purchaseYear, 1, 1), status);
    }

    private TaiSan asset(LocalDate purchaseDate, String status) {
        TaiSan asset = new TaiSan();
        ReflectionTestUtils.setField(asset, "taiSanId", 42);
        asset.setMaTaiSan("ASSET-42");
        asset.setTenTaiSan("Laptop");
        asset.setGiaMua(new BigDecimal("12000000.00"));
        asset.setNgayMua(purchaseDate);
        asset.setTinhTrang(status);
        LoaiTaiSan category = new LoaiTaiSan();
        category.setThoiGianKhauHaoNam(3);
        asset.setLoaiTaiSan(category);
        return asset;
    }
}
