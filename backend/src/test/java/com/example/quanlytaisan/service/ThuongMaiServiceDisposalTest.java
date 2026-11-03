package com.example.quanlytaisan.service;

import com.example.quanlytaisan.dto.ThuongMaiOrderRequest;
import com.example.quanlytaisan.entity.LoaiTaiSan;
import com.example.quanlytaisan.entity.TaiSan;
import com.example.quanlytaisan.entity.ThuongMaiDisposalRecord;
import com.example.quanlytaisan.entity.ThuongMaiOrder;
import com.example.quanlytaisan.entity.ThuongMaiOrderItem;
import com.example.quanlytaisan.repository.AdminMasterDataRepository;
import com.example.quanlytaisan.repository.ConsumableRepository;
import com.example.quanlytaisan.repository.LoaiTaiSanRepository;
import com.example.quanlytaisan.repository.StockReceiptRepository;
import com.example.quanlytaisan.repository.TaiSanRepository;
import com.example.quanlytaisan.repository.ThuongMaiDisposalRecordRepository;
import com.example.quanlytaisan.repository.ThuongMaiOrderRepository;
import com.example.quanlytaisan.repository.TaiSanCapPhatRepository;
import com.example.quanlytaisan.repository.YeuCauBaoTriRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class ThuongMaiServiceDisposalTest {
    private ThuongMaiOrderRepository orderRepository;
    private TaiSanRepository assetRepository;
    private ThuongMaiDisposalRecordRepository recordRepository;
    private TaiSanCapPhatRepository assignmentRepository;
    private YeuCauBaoTriRepository maintenanceRepository;
    private ThuongMaiService service;
    private ThuongMaiOrder order;
    private TaiSan asset;
    private ThuongMaiOrderItem item;

    @BeforeEach
    void setUp() {
        orderRepository = mock(ThuongMaiOrderRepository.class);
        assetRepository = mock(TaiSanRepository.class);
        recordRepository = mock(ThuongMaiDisposalRecordRepository.class);
        assignmentRepository = mock(TaiSanCapPhatRepository.class);
        maintenanceRepository = mock(YeuCauBaoTriRepository.class);
        service = new ThuongMaiService(
                orderRepository,
                assetRepository,
                mock(LoaiTaiSanRepository.class),
                mock(AdminMasterDataRepository.class),
                mock(ConsumableRepository.class),
                mock(StockReceiptRepository.class),
                mock(ActivityLogService.class),
                recordRepository,
                new DisposalRecordPdfGenerator(),
                assignmentRepository,
                maintenanceRepository,
                mock(com.example.quanlytaisan.repository.PurchaseRequestRepository.class));

        order = new ThuongMaiOrder();
        order.setId(12);
        order.setMaDonHang("TL-0000012");
        order.setLoaiDon("BAN");
        order.setTrangThai("CHO_DUYET_THANH_LY");
        order.setLyDoThanhLy("HONG_KHONG_SUA_DUOC");
        order.setGhiChuThanhLy("Hư bo mạch, không thể sửa");
        order.setTongTien(BigDecimal.ZERO);

        LoaiTaiSan category = new LoaiTaiSan();
        category.setThoiGianKhauHaoNam(5);
        asset = new TaiSan();
        asset.setTaiSanId(3);
        asset.setMaTaiSan("TS-0003");
        asset.setTenTaiSan("Laptop");
        asset.setGiaMua(new BigDecimal("1000.00"));
        asset.setNgayMua(LocalDate.now().minusYears(2));
        asset.setLoaiTaiSan(category);
        asset.setTinhTrang("Chờ thanh lý");

        item = new ThuongMaiOrderItem();
        item.setTaiSanId(3);
        item.setMaTaiSan("TS-0003");
        item.setTenSanPham("Laptop");
        item.setSoLuong(1);
        item.setDonGia(BigDecimal.ZERO);
        order.getChiTiet().add(item);

        when(orderRepository.findByIdForUpdate(12)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(assetRepository.findAllByIdForUpdate(List.of(3))).thenReturn(List.of(asset));
        when(assignmentRepository.existsByTaiSan_TaiSanIdAndThuHoiLucIsNull(3)).thenReturn(false);
        when(maintenanceRepository.existsByTaiSan_TaiSanIdAndTrangThaiIn(eq(3), anyList())).thenReturn(false);
        when(recordRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(recordRepository.existsByOrderId(12)).thenReturn(true);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createDisposalOrderUsesTemporaryNumberUntilDatabaseAssignsId() {
        asset.setTinhTrang("Trong kho");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin", "secret", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        doAnswer(invocation -> {
            ThuongMaiOrder savedOrder = invocation.getArgument(0);
            assertTrue(savedOrder.getMaDonHang().matches("TMP-[0-9a-f]{16}"));
            ReflectionTestUtils.setField(savedOrder, "id", 99);
            return savedOrder;
        }).when(orderRepository).saveAndFlush(any(ThuongMaiOrder.class));

        var response = service.createOrder(new ThuongMaiOrderRequest(
                "BAN", "TIEN_MAT", null, null, "MAT", null,
                List.of(new ThuongMaiOrderRequest.Item(
                        3, null, null, null, 1, new BigDecimal("299342.83"),
                        null, null, null, null, null, null)),
                "Đã xác nhận tài sản bị mất", LocalDate.now()));

        assertEquals("TL-0000099", response.maDonHang());
        assertEquals(new BigDecimal("299342.83"), response.tongTien());
        verify(orderRepository).saveAndFlush(any(ThuongMaiOrder.class));
        verify(orderRepository).save(argThat(savedOrder ->
                "TL-0000099".equals(savedOrder.getMaDonHang())));
    }

    @Test
    void approvalRetiresAssetSnapshotsValueAndPersistsSignedPdfRecord() throws Exception {
        var response = service.approveDisposal(12);

        assertEquals("DA_THANH_LY", response.trangThai());
        assertEquals("Đã thanh lý", asset.getTinhTrang());
        assertEquals(LocalDate.now(), order.getNgayThanhLy());
        assertTrue(item.getGiaTriConLai().compareTo(BigDecimal.ZERO) > 0);
        assertEquals(BigDecimal.ZERO, service.remainingValue(asset));
        assertTrue(response.coBienBanThanhLy());

        ArgumentCaptor<ThuongMaiDisposalRecord> record = ArgumentCaptor.forClass(ThuongMaiDisposalRecord.class);
        verify(recordRepository).save(record.capture());
        assertEquals(12, record.getValue().getOrderId());
        assertTrue(record.getValue().getContent().length > 255);
        assertTrue(new String(record.getValue().getContent(), java.nio.charset.StandardCharsets.ISO_8859_1)
                .startsWith("%PDF-"));
        try (PDDocument pdf = Loader.loadPDF(record.getValue().getContent())) {
            assertTrue(pdf.getNumberOfPages() > 0);
        }
    }

    @Test
    void approvalRejectsAssetThatIsStillInUse() {
        asset.setTinhTrang("Đang sử dụng");

        assertThrows(ResponseStatusException.class, () -> service.approveDisposal(12));

        verify(recordRepository, never()).save(any());
        verify(assetRepository, never()).saveAll(any());
    }

    @Test
    void approvalRejectsAssetWithActiveAssignment() {
        when(assignmentRepository.existsByTaiSan_TaiSanIdAndThuHoiLucIsNull(3)).thenReturn(true);

        assertThrows(ResponseStatusException.class, () -> service.approveDisposal(12));

        verify(recordRepository, never()).save(any());
        verify(assetRepository, never()).saveAll(any());
    }

    @Test
    void approvalRejectsAssetWithOpenRepair() {
        when(maintenanceRepository.existsByTaiSan_TaiSanIdAndTrangThaiIn(eq(3), anyList())).thenReturn(true);

        assertThrows(ResponseStatusException.class, () -> service.approveDisposal(12));

        verify(recordRepository, never()).save(any());
        verify(assetRepository, never()).saveAll(any());
    }

    @Test
    void approvalSnapshotsBookValueAtRequestedDisposalDate() {
        LocalDate disposalDate = LocalDate.now().minusMonths(1);
        order.setNgayThanhLy(disposalDate);
        BigDecimal expectedValue = service.remainingValue(asset, disposalDate);

        service.approveDisposal(12);

        assertEquals(disposalDate, order.getNgayThanhLy());
        assertEquals(expectedValue, item.getGiaTriConLai());
    }

    @Test
    void rejectionRecordsDecisionAndLeavesAssetUnchanged() {
        var response = service.rejectDisposal(12, "Không có biên bản kiểm định");

        assertEquals("TU_CHOI_THANH_LY", response.trangThai());
        assertEquals("Không có biên bản kiểm định", response.ghiChuDuyetThanhLy());
        assertEquals("Chờ thanh lý", asset.getTinhTrang());
        verify(assetRepository, never()).saveAll(any());
        verify(recordRepository, never()).save(any());
    }

    @Test
    void rejectionRequiresDecisionNote() {
        assertThrows(ResponseStatusException.class, () -> service.rejectDisposal(12, " "));

        verify(orderRepository, never()).save(any());
    }
}
