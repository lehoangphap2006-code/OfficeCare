package com.example.quanlytaisan.service;

import com.example.quanlytaisan.dto.ThuongMaiOrderResponse;
import com.example.quanlytaisan.dto.ThuongMaiReceiptRequest;
import com.example.quanlytaisan.dto.ManualPurchasePaymentRequest;
import com.example.quanlytaisan.entity.AdminMasterData;
import com.example.quanlytaisan.entity.Consumable;
import com.example.quanlytaisan.entity.LoaiTaiSan;
import com.example.quanlytaisan.entity.StockReceipt;
import com.example.quanlytaisan.entity.TaiSan;
import com.example.quanlytaisan.entity.ThuongMaiOrder;
import com.example.quanlytaisan.entity.ThuongMaiOrderItem;
import com.example.quanlytaisan.repository.ThuongMaiDisposalRecordRepository;
import com.example.quanlytaisan.repository.AdminMasterDataRepository;
import com.example.quanlytaisan.repository.ConsumableRepository;
import com.example.quanlytaisan.repository.LoaiTaiSanRepository;
import com.example.quanlytaisan.repository.StockReceiptRepository;
import com.example.quanlytaisan.repository.PurchaseRequestRepository;
import com.example.quanlytaisan.entity.PurchaseRequest;
import com.example.quanlytaisan.repository.TaiSanRepository;
import com.example.quanlytaisan.repository.ThuongMaiOrderRepository;
import com.example.quanlytaisan.repository.TaiSanCapPhatRepository;
import com.example.quanlytaisan.repository.YeuCauBaoTriRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ThuongMaiServiceStockReceiptTest {
    private ThuongMaiOrderRepository orderRepository;
    private TaiSanRepository assetRepository;
    private LoaiTaiSanRepository categoryRepository;
    private AdminMasterDataRepository masterRepository;
    private ConsumableRepository consumableRepository;
    private StockReceiptRepository receiptRepository;
    private PurchaseRequestRepository purchaseRequestRepository;
    private ThuongMaiService service;
    private ThuongMaiOrder order;
    private ThuongMaiOrderItem line;

    @BeforeEach
    void setUp() {
        orderRepository = mock(ThuongMaiOrderRepository.class);
        assetRepository = mock(TaiSanRepository.class);
        categoryRepository = mock(LoaiTaiSanRepository.class);
        masterRepository = mock(AdminMasterDataRepository.class);
        consumableRepository = mock(ConsumableRepository.class);
        receiptRepository = mock(StockReceiptRepository.class);
        purchaseRequestRepository = mock(PurchaseRequestRepository.class);
        service = new ThuongMaiService(orderRepository, assetRepository, categoryRepository,
                masterRepository, consumableRepository, receiptRepository, mock(ActivityLogService.class),
                mock(ThuongMaiDisposalRecordRepository.class), new DisposalRecordPdfGenerator(),
                mock(TaiSanCapPhatRepository.class), mock(YeuCauBaoTriRepository.class),
                purchaseRequestRepository);

        order = new ThuongMaiOrder();
        order.setId(42);
        order.setMaDonHang("DH-0000042");
        order.setLoaiDon("MUA");
        order.setTrangThai("DA_DAT");
        order.setSupplierName("Nhà cung cấp");
        order.setTongTien(new BigDecimal("300"));
        line = new ThuongMaiOrderItem();
        line.setId(7);
        line.setTenSanPham("Laptop");
        line.setLoaiTaiSanId(3);
        line.setSoLuong(3);
        line.setSoLuongDaNhan(0);
        line.setDonGia(new BigDecimal("100"));
        order.getChiTiet().add(line);

        when(orderRepository.findByIdForUpdate(42)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(receiptRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            StockReceipt receipt = invocation.getArgument(0);
            ReflectionTestUtils.setField(receipt, "id", 1);
            return receipt;
        });
        when(receiptRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        LoaiTaiSan category = new LoaiTaiSan();
        category.setLoaiId(3);
        when(categoryRepository.findById(3)).thenReturn(Optional.of(category));
    }

    @Test
    void partialThenCompleteReceiptsCreateOnlyReceivedAssetsAndKeepReceiptHistory() {
        ThuongMaiOrderResponse first = service.receivePurchaseOrder(42,
                new ThuongMaiReceiptRequest(null, null, List.of(new ThuongMaiReceiptRequest.Item(7, 1, 1, List.of()))));

        assertEquals("NHAN_MOT_PHAN", first.trangThai());
        assertEquals(1, first.chiTiet().get(0).soLuongDaNhan());
        verify(assetRepository, times(1)).save(any());
        ArgumentCaptor<TaiSan> firstAsset = ArgumentCaptor.forClass(TaiSan.class);
        verify(assetRepository).save(firstAsset.capture());
        assertEquals("Bảo trì", firstAsset.getValue().getTinhTrang());
        verify(receiptRepository).save(argThat(receipt ->
                receipt.getItems().get(0).getOrderedQuantity() == 3
                        && receipt.getItems().get(0).getMissingQuantity() == 2));

        order.setTrangThai("NHAN_MOT_PHAN");
        ThuongMaiOrderResponse second = service.receivePurchaseOrder(42,
                new ThuongMaiReceiptRequest(null, null, List.of(new ThuongMaiReceiptRequest.Item(7, 2, 0, List.of()))));

        assertEquals("DA_NHAN", second.trangThai());
        assertEquals(3, second.chiTiet().get(0).soLuongDaNhan());
        verify(assetRepository, times(3)).save(any());
        verify(receiptRepository, times(2)).save(any());
    }

    @Test
    void confirmsManualVietQrPaymentForAnUnpaidPurchaseOrder() {
        order.setTrangThai("DA_DAT");
        order.setTrangThaiThanhToan("CHUA_THANH_TOAN");
        when(orderRepository.existsByMaGiaoDichIgnoreCase("BANK-REF-01")).thenReturn(false);

        ThuongMaiOrderResponse response = service.confirmVietQrPayment(
                42, new ManualPurchasePaymentRequest("  BANK-REF-01  "));

        assertEquals("DA_THANH_TOAN", response.trangThaiThanhToan());
        assertEquals("VIETQR", response.phuongThucThanhToan());
        assertEquals("BANK-REF-01", response.maGiaoDich());
        verify(orderRepository).save(order);
    }

    @Test
    void rejectsDuplicateVietQrPaymentReference() {
        order.setTrangThai("DA_DAT");
        order.setTrangThaiThanhToan("CHUA_THANH_TOAN");
        when(orderRepository.existsByMaGiaoDichIgnoreCase("BANK-REF-01")).thenReturn(true);

        assertThrows(ResponseStatusException.class, () -> service.confirmVietQrPayment(
                42, new ManualPurchasePaymentRequest("BANK-REF-01")));

        verify(orderRepository, never()).save(any());
    }

    @Test
    void rejectsVietQrPaymentForAnAlreadyPaidOrder() {
        order.setTrangThai("DA_DAT");
        order.setTrangThaiThanhToan("DA_THANH_TOAN");

        assertThrows(ResponseStatusException.class, () -> service.confirmVietQrPayment(
                42, new ManualPurchasePaymentRequest("BANK-REF-02")));

        verify(orderRepository, never()).save(any());
    }

    @Test
    void completesLinkedPurchaseRequestOnlyAfterAllItemsAreReceived() {
        PurchaseRequest request = new PurchaseRequest();
        request.setStatus("APPROVED");
        request.setPurchaseOrder(order);
        when(purchaseRequestRepository.findByPurchaseOrder_Id(42)).thenReturn(List.of(request));

        service.receivePurchaseOrder(42, new ThuongMaiReceiptRequest(null, null,
                List.of(new ThuongMaiReceiptRequest.Item(7, 3, 0, List.of()))));

        assertEquals("COMPLETED", request.getStatus());
        verify(purchaseRequestRepository).save(request);
    }

    @Test
    void leavesLinkedPurchaseRequestApprovedAfterPartialReceipt() {
        PurchaseRequest request = new PurchaseRequest();
        request.setStatus("APPROVED");
        request.setPurchaseOrder(order);
        when(purchaseRequestRepository.findByPurchaseOrder_Id(42)).thenReturn(List.of(request));

        service.receivePurchaseOrder(42, new ThuongMaiReceiptRequest(null, null,
                List.of(new ThuongMaiReceiptRequest.Item(7, 1, 0, List.of()))));

        assertEquals("APPROVED", request.getStatus());
        verify(purchaseRequestRepository, never()).save(any());
    }

    @Test
    void rejectsReceiptAboveRemainingOrderQuantity() {
        assertThrows(ResponseStatusException.class, () -> service.receivePurchaseOrder(42,
                new ThuongMaiReceiptRequest(null, null, List.of(new ThuongMaiReceiptRequest.Item(7, 4, 0, List.of())))));

        verify(assetRepository, never()).save(any());
        verify(receiptRepository, never()).saveAndFlush(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void allowsLegacyCompletedOrderWithUnreceivedQuantityToBeReceived() {
        order.setTrangThai("HOAN_TAT");

        ThuongMaiOrderResponse response = service.receivePurchaseOrder(42,
                new ThuongMaiReceiptRequest(null, null,
                        List.of(new ThuongMaiReceiptRequest.Item(7, 1, 0, List.of()))));

        assertEquals("NHAN_MOT_PHAN", response.trangThai());
        assertEquals(1, response.chiTiet().get(0).soLuongDaNhan());
        verify(receiptRepository).save(any());
    }

    @Test
    void rejectsLegacyCompletedOrderWhenAllQuantitiesWereAlreadyReceived() {
        order.setTrangThai("HOAN_TAT");
        line.setSoLuongDaNhan(3);

        assertThrows(ResponseStatusException.class, () -> service.receivePurchaseOrder(42,
                new ThuongMaiReceiptRequest(null, null,
                        List.of(new ThuongMaiReceiptRequest.Item(7, 1, 0, List.of())))));

        verify(receiptRepository, never()).saveAndFlush(any());
        verify(assetRepository, never()).save(any());
    }

    @Test
    void receivedAssetDetailsAndLineNotesAreSavedToTheAssetAndImmutableReceiptHistory() {
        AdminMasterData location = new AdminMasterData();
        ReflectionTestUtils.setField(location, "id", 11);
        location.setKind("LOCATION");
        location.setBuilding("Tòa A");
        location.setFloor("2");
        location.setRoom("201");
        when(masterRepository.findById(11)).thenReturn(Optional.of(location));
        LocalDate warrantyEndDate = LocalDate.now().plusYears(2);
        ThuongMaiReceiptRequest.AssetDetails details = new ThuongMaiReceiptRequest.AssetDetails(
                "SN-001", "Lenovo", "ThinkPad", warrantyEndDate, 11);
        ThuongMaiReceiptRequest.Item receiptItem = new ThuongMaiReceiptRequest.Item(
                7, 1, 0, null, List.of(details), "Thùng hàng nguyên vẹn");

        service.receivePurchaseOrder(42, new ThuongMaiReceiptRequest(
                LocalDate.now(), null, List.of(receiptItem)));

        ArgumentCaptor<TaiSan> assetCaptor = ArgumentCaptor.forClass(TaiSan.class);
        verify(assetRepository).save(assetCaptor.capture());
        TaiSan asset = assetCaptor.getValue();
        assertEquals("SN-001", asset.getSerialNumber());
        assertEquals("Lenovo", asset.getBrand());
        assertEquals("ThinkPad", asset.getModel());
        assertEquals(warrantyEndDate, asset.getWarrantyEndDate());
        assertEquals("Tòa A", asset.getToaNha());
        assertEquals("2", asset.getTang());
        assertEquals("201", asset.getPhong());
        verify(receiptRepository).save(argThat(receipt ->
                "Thùng hàng nguyên vẹn".equals(receipt.getItems().get(0).getNotes())));
    }

    @Test
    void requiresSerialForCategoriesConfiguredToRequireIt() {
        LoaiTaiSan category = new LoaiTaiSan();
        category.setLoaiId(3);
        category.setRequiresSerial(true);
        when(categoryRepository.findById(3)).thenReturn(Optional.of(category));

        assertThrows(ResponseStatusException.class, () -> service.receivePurchaseOrder(42,
                new ThuongMaiReceiptRequest(null, null,
                        List.of(new ThuongMaiReceiptRequest.Item(7, 1, 0, List.of())))));

        verify(assetRepository, never()).save(any());
        verify(receiptRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsSerialAlreadyUsedByAnotherAsset() {
        when(assetRepository.existsBySerialNumberIgnoreCase("SN-USED")).thenReturn(true);
        ThuongMaiReceiptRequest.AssetDetails details = new ThuongMaiReceiptRequest.AssetDetails(
                "SN-USED", null, null, null, null);

        assertThrows(ResponseStatusException.class, () -> service.receivePurchaseOrder(42,
                new ThuongMaiReceiptRequest(null, null, List.of(
                        new ThuongMaiReceiptRequest.Item(7, 1, 0, null, List.of(details), null)))));

        verify(assetRepository, never()).save(any());
        verify(receiptRepository, never()).saveAndFlush(any());
    }

    @Test
    void allowsSomeSerialsToBeOmittedWhenCategoryDoesNotRequireThem() {
        AdminMasterData location = new AdminMasterData();
        location.setKind("LOCATION");
        location.setBuilding("Tòa A");
        when(masterRepository.findById(11)).thenReturn(Optional.of(location));
        line.setSoLuong(2);
        ThuongMaiReceiptRequest.Item receiptItem = new ThuongMaiReceiptRequest.Item(
                7, 2, 0, null, List.of(
                        new ThuongMaiReceiptRequest.AssetDetails("SN-001", null, null, null, 11),
                        new ThuongMaiReceiptRequest.AssetDetails(null, null, null, null, 11)), null);

        service.receivePurchaseOrder(42, new ThuongMaiReceiptRequest(null, null, List.of(receiptItem)));

        ArgumentCaptor<TaiSan> assets = ArgumentCaptor.forClass(TaiSan.class);
        verify(assetRepository, times(2)).save(assets.capture());
        assertEquals("SN-001", assets.getAllValues().get(0).getSerialNumber());
        assertNull(assets.getAllValues().get(1).getSerialNumber());
    }

    @Test
    void damagedConsumablesAreRecordedButOnlyGoodQuantityIsAddedToStock() {
        line.setLoaiDong("VAT_TU");
        line.setConsumableId(9);
        line.setLoaiTaiSanId(null);
        line.setSoLuong(5);
        Consumable consumable = new Consumable();
        consumable.setStockQuantity(new BigDecimal("4"));
        when(consumableRepository.findByIdForUpdate(9)).thenReturn(Optional.of(consumable));

        ThuongMaiOrderResponse response = service.receivePurchaseOrder(42,
                new ThuongMaiReceiptRequest(null, "Giao một phần", List.of(new ThuongMaiReceiptRequest.Item(7, 3, 1, List.of()))));

        assertEquals("NHAN_MOT_PHAN", response.trangThai());
        assertEquals(new BigDecimal("6"), consumable.getStockQuantity());
        verify(assetRepository, never()).save(any());
    }
}
