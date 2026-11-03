package com.example.quanlytaisan.service;

import com.example.quanlytaisan.entity.PurchaseRequest;
import com.example.quanlytaisan.entity.ThuongMaiOrder;
import com.example.quanlytaisan.entity.ThuongMaiOrderItem;
import com.example.quanlytaisan.repository.AdminInvoiceRepository;
import com.example.quanlytaisan.repository.PurchaseRequestRepository;
import com.example.quanlytaisan.repository.StockReceiptRepository;
import com.example.quanlytaisan.repository.ThuongMaiOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class PurchaseOrderCleanupServiceTest {
    private ThuongMaiOrderRepository orderRepository;
    private PurchaseRequestRepository requestRepository;
    private StockReceiptRepository receiptRepository;
    private AdminInvoiceRepository invoiceRepository;
    private ActivityLogService activityLog;
    private PurchaseOrderCleanupService service;

    @BeforeEach
    void setUp() {
        orderRepository = mock(ThuongMaiOrderRepository.class);
        requestRepository = mock(PurchaseRequestRepository.class);
        receiptRepository = mock(StockReceiptRepository.class);
        invoiceRepository = mock(AdminInvoiceRepository.class);
        activityLog = mock(ActivityLogService.class);
        service = new PurchaseOrderCleanupService(orderRepository, requestRepository,
                receiptRepository, invoiceRepository, activityLog);
    }

    @Test
    void deletesOnlyUnreceivedUnpaidOrdersAndKeepsLinkedRequests() {
        ThuongMaiOrder order = order(1, "DH00000001");
        PurchaseRequest request = new PurchaseRequest();
        request.setPurchaseOrder(order);
        when(orderRepository.findAll()).thenReturn(List.of(order));
        when(receiptRepository.countByPurchaseOrderId(1)).thenReturn(0L);
        when(invoiceRepository.existsByPurchaseOrderId(1)).thenReturn(false);
        when(requestRepository.findByPurchaseOrder_Id(1)).thenReturn(List.of(request));

        var result = service.deleteUnreceivedPurchaseOrders("admin");

        assertEquals(List.of("DH00000001"), result.deletedOrderNumbers());
        assertEquals(List.of(), result.retainedOrders());
        assertEquals(null, request.getPurchaseOrder());
        verify(requestRepository).saveAll(List.of(request));
        verify(orderRepository).delete(order);
        verify(activityLog).record(eq("admin"), eq("DELETE_UNRECEIVED_PURCHASE_ORDER"),
                eq("ThuongMaiOrder"), eq(1), any(), isNull(), isNull());
    }

    @Test
    void preservesOrdersWithReceiptsOrInvoices() {
        ThuongMaiOrder withReceipt = order(1, "DH00000001");
        ThuongMaiOrder withInvoice = order(2, "DH00000002");
        when(orderRepository.findAll()).thenReturn(List.of(withReceipt, withInvoice));
        when(receiptRepository.countByPurchaseOrderId(1)).thenReturn(1L);
        when(receiptRepository.countByPurchaseOrderId(2)).thenReturn(0L);
        when(invoiceRepository.existsByPurchaseOrderId(2)).thenReturn(true);

        var result = service.deleteUnreceivedPurchaseOrders("admin");

        assertEquals(List.of(), result.deletedOrderNumbers());
        assertEquals(List.of(
                new PurchaseOrderCleanupService.RetainedOrder("DH00000001", "Đã có phiếu nhập kho"),
                new PurchaseOrderCleanupService.RetainedOrder("DH00000002", "Đã liên kết hóa đơn")),
                result.retainedOrders());
        verify(orderRepository, never()).delete(any(ThuongMaiOrder.class));
    }

    @Test
    void preservesOrdersWithReceivedAssetsOrCompletedStatus() {
        ThuongMaiOrder withReceivedAsset = order(1, "DH00000001");
        withReceivedAsset.getChiTiet().get(0).setSoLuongDaNhan(1);
        ThuongMaiOrder completed = order(2, "DH00000002");
        completed.setTrangThai("DA_NHAN");
        when(orderRepository.findAll()).thenReturn(List.of(withReceivedAsset, completed));
        when(receiptRepository.countByPurchaseOrderId(anyInt())).thenReturn(0L);
        when(invoiceRepository.existsByPurchaseOrderId(anyInt())).thenReturn(false);

        var result = service.deleteUnreceivedPurchaseOrders("admin");

        assertEquals(List.of(), result.deletedOrderNumbers());
        assertEquals(2, result.retainedOrders().size());
        verify(orderRepository, never()).delete(any(ThuongMaiOrder.class));
    }

    private ThuongMaiOrder order(int id, String number) {
        ThuongMaiOrder order = new ThuongMaiOrder();
        order.setId(id);
        order.setMaDonHang(number);
        order.setLoaiDon("MUA");
        order.setTrangThai("DA_DAT");
        order.setTrangThaiThanhToan("CHUA_TRA");
        order.setTongTien(new BigDecimal("100"));
        ThuongMaiOrderItem item = new ThuongMaiOrderItem();
        item.setDonHang(order);
        item.setSoLuong(2);
        item.setSoLuongDaNhan(0);
        order.setChiTiet(List.of(item));
        return order;
    }
}
