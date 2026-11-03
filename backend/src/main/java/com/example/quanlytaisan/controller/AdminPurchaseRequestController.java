package com.example.quanlytaisan.controller;

import com.example.quanlytaisan.dto.PurchaseRequestDtos.Response;
import com.example.quanlytaisan.dto.PurchaseRequestDtos.ReviewRequest;
import com.example.quanlytaisan.dto.PurchaseRequestDtos.PlaceOrderRequest;
import com.example.quanlytaisan.dto.ThuongMaiOrderRequest;
import com.example.quanlytaisan.entity.LoaiTaiSan;
import com.example.quanlytaisan.entity.PurchaseRequest;
import com.example.quanlytaisan.entity.ThuongMaiOrder;
import com.example.quanlytaisan.repository.LoaiTaiSanRepository;
import com.example.quanlytaisan.repository.PurchaseRequestRepository;
import com.example.quanlytaisan.repository.ThuongMaiOrderRepository;
import com.example.quanlytaisan.service.ActivityLogService;
import com.example.quanlytaisan.service.ThuongMaiService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/api/admin/purchase-requests")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPurchaseRequestController {
    private final PurchaseRequestRepository requestRepository;
    private final LoaiTaiSanRepository categoryRepository;
    private final ActivityLogService activityLog;
    private final ThuongMaiService commerceService;
    private final ThuongMaiOrderRepository orderRepository;

    public AdminPurchaseRequestController(PurchaseRequestRepository requestRepository,
            LoaiTaiSanRepository categoryRepository, ActivityLogService activityLog,
            ThuongMaiService commerceService, ThuongMaiOrderRepository orderRepository) {
        this.requestRepository = requestRepository;
        this.categoryRepository = categoryRepository;
        this.activityLog = activityLog;
        this.commerceService = commerceService;
        this.orderRepository = orderRepository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<Response> requests() {
        return requestRepository.findTop100ByOrderByCreatedAtDesc().stream().map(this::response).toList();
    }

    @GetMapping("/pending-count")
    @Transactional(readOnly = true)
    public long pendingCount() {
        return requestRepository.countByStatus("PENDING");
    }

    @PutMapping("/{id}")
    @Transactional
    public Response review(@PathVariable Integer id, @RequestBody ReviewRequest request, Authentication authentication) {
        if (request == null || request.status() == null || !List.of("APPROVED", "REJECTED").contains(request.status())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trạng thái duyệt không hợp lệ");
        }
        PurchaseRequest row = requestRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu mua sắm"));
        if (!"PENDING".equals(row.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Yêu cầu này đã được xử lý");
        }
        String note = request.reviewNote() == null || request.reviewNote().isBlank() ? null : request.reviewNote().trim();
        if (note != null && note.length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ghi chú duyệt tối đa 500 ký tự");
        }
        if ("REJECTED".equals(request.status()) && note == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng nhập lý do từ chối");
        }
        Response old = response(row);
        row.setStatus(request.status());
        row.setReviewer(authentication.getName());
        row.setReviewNote(note);
        row.setReviewedAt(Instant.now());
        Response updated = response(requestRepository.save(row));
        activityLog.record(authentication.getName(), request.status(), "PurchaseRequest", id, old, updated, null);
        return updated;
    }

    @PostMapping("/{id}/place-order")
    @Transactional
    public Response placeOrder(@PathVariable Integer id, @RequestBody PlaceOrderRequest request,
            Authentication authentication) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thông tin đặt hàng không hợp lệ");
        }
        if (!isUuid(request.requestId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã yêu cầu tạo đơn không hợp lệ");
        }
        PurchaseRequest row = requestRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu mua sắm"));
        if (row.getPurchaseOrder() != null) {
            if (request.requestId().equals(row.getOrderRequestId())) {
                return response(row);
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Yêu cầu này đã được chuyển thành đơn mua");
        }
        if (requestRepository.findByOrderRequestId(request.requestId()).isPresent()
                || orderRepository.findByClientRequestId(request.requestId()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Mã yêu cầu tạo đơn đã được sử dụng");
        }
        if (!"APPROVED".equals(row.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Chỉ yêu cầu đã duyệt mới được đặt hàng");
        }
        if (row.getCategoryId() == null || !categoryRepository.existsById(row.getCategoryId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Danh mục tài sản của yêu cầu không còn tồn tại. Hãy kiểm tra danh mục trước khi tạo đơn mua.");
        }
        ThuongMaiOrderRequest orderRequest = new ThuongMaiOrderRequest(
                "MUA",
                request.paymentMethod(),
                request.supplierId(),
                request.expectedDeliveryDate(),
                null,
                null,
                List.of(new ThuongMaiOrderRequest.Item(
                        null,
                        row.getCategoryId(),
                        request.locationId(),
                        row.getAssetName(),
                        row.getQuantity(),
                        request.unitPrice(),
                        null,
                        null,
                        null,
                        "TAI_SAN",
                        null,
                        null)),
                null,
                null,
                request.requestId());
        var createdOrder = commerceService.createOrder(orderRequest);
        ThuongMaiOrder order = orderRepository.findById(createdOrder.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Không tìm thấy đơn mua vừa tạo"));
        Response old = response(row);
        row.setPurchaseOrder(order);
        row.setOrderRequestId(request.requestId());
        Response updated = response(requestRepository.save(row));
        activityLog.record(authentication.getName(), "PLACE_ORDER", "PurchaseRequest", id, old, updated,
                "Đã tạo đơn mua " + order.getMaDonHang() + " từ yêu cầu được duyệt.");
        return updated;
    }

    private boolean isUuid(String value) {
        if (value == null) return false;
        try {
            return UUID.fromString(value).toString().equalsIgnoreCase(value);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private Response response(PurchaseRequest row) {
        String category = row.getCategoryId() == null ? null : categoryRepository.findById(row.getCategoryId())
                .map(LoaiTaiSan::getTenLoai).orElse(null);
        return new Response(row.getId(), row.getRequester(), row.getAssetName(), row.getCategoryId(),
                category, row.getQuantity(), row.getReason(), row.getEstimatedAmount(), row.getStatus(), row.getReviewer(),
                row.getReviewNote(), row.getCreatedAt(), row.getReviewedAt(),
                row.getPurchaseOrder() == null ? null : row.getPurchaseOrder().getId(),
                row.getPurchaseOrder() == null ? null : row.getPurchaseOrder().getMaDonHang(),
                row.getPurchaseOrder() == null ? null : row.getPurchaseOrder().getTrangThai());
    }
}
