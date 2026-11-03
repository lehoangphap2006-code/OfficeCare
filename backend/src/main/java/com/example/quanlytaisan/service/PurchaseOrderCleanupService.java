package com.example.quanlytaisan.service;

import com.example.quanlytaisan.entity.PurchaseRequest;
import com.example.quanlytaisan.entity.ThuongMaiOrder;
import com.example.quanlytaisan.repository.AdminInvoiceRepository;
import com.example.quanlytaisan.repository.PurchaseRequestRepository;
import com.example.quanlytaisan.repository.StockReceiptRepository;
import com.example.quanlytaisan.repository.ThuongMaiOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class PurchaseOrderCleanupService {
    private final ThuongMaiOrderRepository orderRepository;
    private final PurchaseRequestRepository purchaseRequestRepository;
    private final StockReceiptRepository receiptRepository;
    private final AdminInvoiceRepository invoiceRepository;
    private final ActivityLogService activityLog;

    public PurchaseOrderCleanupService(
            ThuongMaiOrderRepository orderRepository,
            PurchaseRequestRepository purchaseRequestRepository,
            StockReceiptRepository receiptRepository,
            AdminInvoiceRepository invoiceRepository,
            ActivityLogService activityLog) {
        this.orderRepository = orderRepository;
        this.purchaseRequestRepository = purchaseRequestRepository;
        this.receiptRepository = receiptRepository;
        this.invoiceRepository = invoiceRepository;
        this.activityLog = activityLog;
    }

    @Transactional
    public CleanupResult deleteUnreceivedPurchaseOrders(String actor) {
        List<String> deleted = new ArrayList<>();
        List<RetainedOrder> retained = new ArrayList<>();

        for (ThuongMaiOrder order : orderRepository.findAll()) {
            if (!"MUA".equals(order.getLoaiDon())) {
                continue;
            }

            String reason = retentionReason(order);
            if (reason != null) {
                retained.add(new RetainedOrder(order.getMaDonHang(), reason));
                continue;
            }

            List<PurchaseRequest> requests = purchaseRequestRepository.findByPurchaseOrder_Id(order.getId());
            requests.forEach(request -> request.setPurchaseOrder(null));
            purchaseRequestRepository.saveAll(requests);

            activityLog.record(actor, "DELETE_UNRECEIVED_PURCHASE_ORDER", "ThuongMaiOrder", order.getId(),
                    Map.of("maDonHang", order.getMaDonHang(), "trangThai", order.getTrangThai()),
                    null, null);
            orderRepository.delete(order);
            deleted.add(order.getMaDonHang());
        }

        return new CleanupResult(deleted, retained);
    }

    private String retentionReason(ThuongMaiOrder order) {
        if (receiptRepository.countByPurchaseOrderId(order.getId()) > 0) {
            return "Đã có phiếu nhập kho";
        }
        if (invoiceRepository.existsByPurchaseOrderId(order.getId())) {
            return "Đã liên kết hóa đơn";
        }
        if (order.getChiTiet().stream().anyMatch(item ->
                item.getSoLuongDaNhan() > 0 || item.getTaiSanId() != null)) {
            return "Đã ghi nhận hàng nhận hoặc tài sản";
        }
        if (!"DA_DAT".equals(order.getTrangThai()) && !"DA_HUY".equals(order.getTrangThai())) {
            return "Trạng thái đơn không còn là mới đặt hoặc đã hủy";
        }
        if (!"CHUA_TRA".equals(order.getTrangThaiThanhToan()) || order.getMaGiaoDich() != null) {
            return "Đã phát sinh thanh toán hoặc mã giao dịch";
        }
        return null;
    }

    public record CleanupResult(List<String> deletedOrderNumbers, List<RetainedOrder> retainedOrders) {}

    public record RetainedOrder(String orderNumber, String reason) {}
}
