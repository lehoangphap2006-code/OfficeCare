package com.example.quanlytaisan.service;

import com.example.quanlytaisan.dto.ThuongMaiOrderRequest;
import com.example.quanlytaisan.dto.ThuongMaiOrderResponse;
import com.example.quanlytaisan.dto.ThuongMaiReceiptRequest;
import com.example.quanlytaisan.dto.StockReceiptDtos;
import com.example.quanlytaisan.dto.ManualPurchasePaymentRequest;
import com.example.quanlytaisan.entity.AdminMasterData;
import com.example.quanlytaisan.entity.Consumable;
import com.example.quanlytaisan.entity.LoaiTaiSan;
import com.example.quanlytaisan.entity.TaiSan;
import com.example.quanlytaisan.entity.StockReceipt;
import com.example.quanlytaisan.entity.StockReceiptItem;
import com.example.quanlytaisan.entity.ThuongMaiOrder;
import com.example.quanlytaisan.entity.ThuongMaiOrderItem;
import com.example.quanlytaisan.entity.ThuongMaiDisposalRecord;
import com.example.quanlytaisan.repository.LoaiTaiSanRepository;
import com.example.quanlytaisan.repository.AdminMasterDataRepository;
import com.example.quanlytaisan.repository.ConsumableRepository;
import com.example.quanlytaisan.repository.StockReceiptRepository;
import com.example.quanlytaisan.repository.TaiSanRepository;
import com.example.quanlytaisan.repository.TaiSanCapPhatRepository;
import com.example.quanlytaisan.repository.ThuongMaiOrderRepository;
import com.example.quanlytaisan.repository.ThuongMaiDisposalRecordRepository;
import com.example.quanlytaisan.repository.YeuCauBaoTriRepository;
import com.example.quanlytaisan.repository.PurchaseRequestRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.math.RoundingMode;

@Service
public class ThuongMaiService {
    private static final String IN_STOCK = "Trong kho";
    private static final String WAITING_DISPOSAL = "Chờ thanh lý";
    private static final String RETIRED = "Đã thanh lý";
    private static final String PENDING = "CHO_THANH_TOAN";
    private static final String COMPLETE = "HOAN_TAT";
    private static final String PENDING_APPROVAL = "CHO_DUYET";
    private static final String ORDERED = "DA_DAT";
    private static final String PARTIALLY_RECEIVED = "NHAN_MOT_PHAN";
    private static final String RECEIVED = "DA_NHAN";
    private static final String DISPOSAL_APPROVAL = "CHO_DUYET_THANH_LY";
    private static final String DISPOSED = "DA_THANH_LY";
    private static final String UNPAID = "CHUA_THANH_TOAN";
    private static final String PAID = "DA_THANH_TOAN";
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999999.99");

    private final ThuongMaiOrderRepository orderRepository;
    private final TaiSanRepository assetRepository;
    private final LoaiTaiSanRepository categoryRepository;
    private final AdminMasterDataRepository masterRepository;
    private final ConsumableRepository consumableRepository;
    private final StockReceiptRepository receiptRepository;
    private final ActivityLogService activityLogService;
    private final ThuongMaiDisposalRecordRepository disposalRecordRepository;
    private final DisposalRecordPdfGenerator disposalRecordPdfGenerator;
    private final TaiSanCapPhatRepository assignmentRepository;
    private final YeuCauBaoTriRepository maintenanceRepository;
    private final PurchaseRequestRepository purchaseRequestRepository;

    public ThuongMaiService(
            ThuongMaiOrderRepository orderRepository,
            TaiSanRepository assetRepository,
            LoaiTaiSanRepository categoryRepository,
            AdminMasterDataRepository masterRepository,
            ConsumableRepository consumableRepository,
            StockReceiptRepository receiptRepository,
            ActivityLogService activityLogService,
            ThuongMaiDisposalRecordRepository disposalRecordRepository,
            DisposalRecordPdfGenerator disposalRecordPdfGenerator,
            TaiSanCapPhatRepository assignmentRepository,
            YeuCauBaoTriRepository maintenanceRepository,
            PurchaseRequestRepository purchaseRequestRepository) {
        this.orderRepository = orderRepository;
        this.assetRepository = assetRepository;
        this.categoryRepository = categoryRepository;
        this.masterRepository = masterRepository;
        this.consumableRepository = consumableRepository;
        this.receiptRepository = receiptRepository;
        this.activityLogService = activityLogService;
        this.disposalRecordRepository = disposalRecordRepository;
        this.disposalRecordPdfGenerator = disposalRecordPdfGenerator;
        this.assignmentRepository = assignmentRepository;
        this.maintenanceRepository = maintenanceRepository;
        this.purchaseRequestRepository = purchaseRequestRepository;
    }

    @Transactional(readOnly = true)
    public List<TaiSan> saleableAssets() {
        return java.util.stream.Stream.concat(
                        java.util.stream.Stream.concat(
                                assetRepository.findByTinhTrangOrderByTaiSanIdAsc(IN_STOCK).stream(),
                                assetRepository.findByTinhTrangOrderByTaiSanIdAsc(WAITING_DISPOSAL).stream()),
                        assetRepository.findByTinhTrangOrderByTaiSanIdAsc("Thiếu").stream())
                .filter(asset -> !orderRepository.isAssetPendingSale(asset.getTaiSanId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public BigDecimal remainingValue(TaiSan asset) {
        return remainingValue(asset, LocalDate.now());
    }

    public BigDecimal remainingValue(TaiSan asset, LocalDate asOfDate) {
        if (RETIRED.equals(asset.getTinhTrang()) || "Thanh lý".equals(asset.getTinhTrang())) {
            return BigDecimal.ZERO;
        }
        BigDecimal purchaseValue = asset.getGiaMua();
        LocalDate purchaseDate = asset.getNgayMua();
        if (purchaseValue == null || purchaseDate == null || asset.getLoaiTaiSan() == null) return BigDecimal.ZERO;
        int usefulLifeYears = asset.getLoaiTaiSan().getThoiGianKhauHaoNam();
        if (usefulLifeYears < 1) return purchaseValue.setScale(2, RoundingMode.HALF_UP);
        long daysElapsed = Math.max(0, ChronoUnit.DAYS.between(purchaseDate, asOfDate));
        long usefulLifeDays = ChronoUnit.DAYS.between(purchaseDate, purchaseDate.plusYears(usefulLifeYears));
        BigDecimal depreciation = purchaseValue.multiply(BigDecimal.valueOf(daysElapsed))
                .divide(BigDecimal.valueOf(usefulLifeDays), 10, RoundingMode.HALF_UP);
        return purchaseValue.subtract(depreciation).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    @Transactional(readOnly = true)
    public List<ThuongMaiOrderResponse> orders() {
        return orderRepository.findTop100ByOrderByIdDesc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<StockReceiptDtos.Response> receipts() {
        return receiptRepository.findTop100ByOrderByIdDesc().stream().map(this::toReceiptResponse).toList();
    }

    @Transactional
    public ThuongMaiOrderResponse createOrder(ThuongMaiOrderRequest request) {
        if (request == null || (!"MUA".equals(request.loaiDon()) && !"BAN".equals(request.loaiDon()))) {
            throw badRequest("Loại đơn hàng không hợp lệ");
        }
        if (request.requestId() != null) {
            try {
                java.util.UUID.fromString(request.requestId());
            } catch (IllegalArgumentException exception) {
                throw badRequest("Mã yêu cầu tạo đơn không hợp lệ");
            }
            var existingOrder = orderRepository.findByClientRequestId(request.requestId());
            if (existingOrder.isPresent()) return toResponse(existingOrder.get());
        }
        if ("BAN".equals(request.loaiDon()) && !isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chỉ quản trị viên mới được tạo phiếu thanh lý");
        }
        if ("MUA".equals(request.loaiDon()) && !"TIEN_MAT".equals(request.phuongThucThanhToan())
                && !"MOCK_ONLINE".equals(request.phuongThucThanhToan())
                && !"VIETQR".equals(request.phuongThucThanhToan())
                && !"MOMO".equals(request.phuongThucThanhToan())) {
            throw badRequest("Phương thức thanh toán không hợp lệ");
        }
        if ("BAN".equals(request.loaiDon()) && (request.lyDoThanhLy() == null
                || !Set.of("HONG", "HONG_KHONG_SUA_DUOC", "LOI_THOI", "MAT", "NHUONG_LAI", "HET_NIEN_HAN", "KHAC")
                        .contains(request.lyDoThanhLy()))) {
            throw badRequest("Vui lòng chọn lý do thanh lý hợp lệ");
        }
        if ("BAN".equals(request.loaiDon()) && request.phuongThucNhanTien() != null
                && !Set.of("CHUYEN_KHOAN", "NOP_QUY").contains(request.phuongThucNhanTien())) {
            throw badRequest("Phương thức nhận tiền phải là chuyển khoản hoặc nộp quỹ");
        }
        if ("BAN".equals(request.loaiDon()) && request.ghiChuThanhLy() != null
                && request.ghiChuThanhLy().trim().length() > 1000) {
            throw badRequest("Chi tiết lý do thanh lý tối đa 1.000 ký tự");
        }
        if ("BAN".equals(request.loaiDon()) && "KHAC".equals(request.lyDoThanhLy())
                && (request.ghiChuThanhLy() == null || request.ghiChuThanhLy().isBlank())) {
            throw badRequest("Vui lòng mô tả lý do khác khi thanh lý");
        }
        LocalDate disposalDate = request.ngayThanhLy() == null ? LocalDate.now() : request.ngayThanhLy();
        if ("BAN".equals(request.loaiDon()) && disposalDate.isAfter(LocalDate.now())) {
            throw badRequest("Ngày thanh lý không được ở tương lai");
        }
        if (request.chiTiet() == null || request.chiTiet().isEmpty() || request.chiTiet().size() > 30) {
            throw badRequest("Đơn hàng phải có từ 1 đến 30 dòng sản phẩm");
        }
        AdminMasterData supplier = null;
        if ("MUA".equals(request.loaiDon())) {
            if (request.nhaCungCapId() == null) {
                throw badRequest("Vui lòng chọn nhà cung cấp");
            }
            supplier = masterRepository.findById(request.nhaCungCapId())
                    .filter(row -> "SUPPLIER".equals(row.getKind()))
                    .orElseThrow(() -> badRequest("Nhà cung cấp không tồn tại"));
            if (request.ngayDuKienGiao() == null || request.ngayDuKienGiao().isBefore(LocalDate.now())) {
                throw badRequest("Ngày dự kiến giao phải là hôm nay hoặc trong tương lai");
            }
        }
        List<TaiSan> saleAssets = request.loaiDon().equals("BAN")
                ? lockAndValidateSaleAssets(request.chiTiet())
                : List.of();
        Map<Integer, TaiSan> saleAssetById = new HashMap<>();
        saleAssets.forEach(asset -> saleAssetById.put(asset.getTaiSanId(), asset));

        ThuongMaiOrder order = new ThuongMaiOrder();
        order.setClientRequestId(request.requestId());
        order.setLoaiDon(request.loaiDon());
        order.setPhuongThucThanhToan("BAN".equals(request.loaiDon()) ? "TIEN_MAT" : request.phuongThucThanhToan());
        order.setTrangThai("MUA".equals(request.loaiDon()) ? ORDERED : DISPOSAL_APPROVAL);
        order.setTrangThaiThanhToan(UNPAID);
        order.setTongTien(BigDecimal.ZERO);
        if (supplier != null) {
            order.setSupplierId(supplier.getId());
            order.setSupplierName(supplier.getName());
            order.setNgayDuKienGiao(request.ngayDuKienGiao());
            order.setNguoiYeuCau(actor());
        }
        if ("BAN".equals(request.loaiDon())) {
            order.setLyDoThanhLy(request.lyDoThanhLy());
            order.setNgayThanhLy(disposalDate);
            order.setGhiChuThanhLy(request.ghiChuThanhLy() == null || request.ghiChuThanhLy().isBlank()
                    ? null : request.ghiChuThanhLy().trim());
            order.setPhuongThucNhanTien(request.phuongThucNhanTien());
            order.setNguoiYeuCau(actor());
        }

        BigDecimal total = BigDecimal.ZERO;
        int totalQuantity = 0;
        for (ThuongMaiOrderRequest.Item input : request.chiTiet()) {
            ThuongMaiOrderItem item = request.loaiDon().equals("MUA")
                    ? createPurchaseItem(input)
                    : createSaleItem(input, saleAssetById, disposalDate);
            totalQuantity += item.getSoLuong();
            if (totalQuantity > 1000) {
                throw badRequest("Tổng số lượng trong đơn không được vượt quá 1.000");
            }
            BigDecimal lineTotal = item.getDonGia().multiply(BigDecimal.valueOf(item.getSoLuong()));
            total = total.add(lineTotal);
            if (total.compareTo(MAX_AMOUNT) > 0) {
                throw badRequest("Tổng đơn hàng vượt quá giới hạn cho phép");
            }
            item.setDonHang(order);
            order.getChiTiet().add(item);
        }
        order.setTongTien(total);
        order.setMaDonHang("TMP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        orderRepository.saveAndFlush(order);
        order.setMaDonHang(String.format(Locale.ROOT, "MUA".equals(request.loaiDon()) ? "DH-%07d" : "TL-%07d", order.getId()));
        ThuongMaiOrder saved = orderRepository.save(order);
        ThuongMaiOrderResponse response = toResponse(saved);
        activityLogService.record(actor(), "CREATE", "ThuongMaiOrder", saved.getId(), null, response,
                "Đơn hàng " + saved.getMaDonHang() + " đã được tạo.");
        return response;
    }

    @Transactional
    public ThuongMaiOrderResponse simulateOnlinePayment(Integer orderId) {
        ThuongMaiOrder order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng"));
        ensureOrderAccess(order);
        if (!"MOCK_ONLINE".equals(order.getPhuongThucThanhToan())) {
            throw badRequest("Đơn hàng này không sử dụng thanh toán trực tuyến");
        }
        if (COMPLETE.equals(order.getTrangThai())
                || ("MUA".equals(order.getLoaiDon()) && RECEIVED.equals(order.getTrangThai())
                && PAID.equals(order.getTrangThaiThanhToan()))) {
            return toResponse(order);
        }
        boolean eligible = "MUA".equals(order.getLoaiDon())
                ? RECEIVED.equals(order.getTrangThai())
                : PENDING.equals(order.getTrangThai());
        if (!eligible) {
            throw badRequest("Đơn hàng chưa ở trạng thái có thể thanh toán");
        }

        ThuongMaiOrderResponse previous = toResponse(order);
        if ("BAN".equals(order.getLoaiDon())) completeOrder(order);
        else order.setTrangThaiThanhToan(PAID);
        order.setMaGiaoDich("MOCK-" + order.getMaDonHang());
        ThuongMaiOrder saved = orderRepository.save(order);
        ThuongMaiOrderResponse response = toResponse(saved);
        activityLogService.record(actor(), "PAY", "ThuongMaiOrder", saved.getId(), previous, response,
                "Đơn hàng " + saved.getMaDonHang() + " đã thanh toán.");
        return response;
    }

    @Transactional
    public ThuongMaiOrderResponse confirmVietQrPayment(Integer orderId, ManualPurchasePaymentRequest request) {
        ThuongMaiOrder order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn mua"));
        ensureOrderAccess(order);
        if (!"MUA".equals(order.getLoaiDon())) {
            throw badRequest("Chỉ xác nhận thanh toán VietQR cho đơn mua");
        }
        if (!List.of(ORDERED, PARTIALLY_RECEIVED, RECEIVED, COMPLETE).contains(order.getTrangThai())) {
            throw badRequest("Đơn mua chưa ở trạng thái có thể thanh toán");
        }
        if (PAID.equals(order.getTrangThaiThanhToan())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Đơn mua đã được thanh toán");
        }
        if (!UNPAID.equals(order.getTrangThaiThanhToan())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Trạng thái thanh toán đơn mua không hợp lệ");
        }
        String reference = request == null || request.transactionReference() == null
                ? null : request.transactionReference().trim();
        if (reference == null || reference.isBlank() || reference.length() > 40
                || reference.chars().anyMatch(Character::isISOControl)) {
            throw badRequest("Vui lòng nhập mã tham chiếu giao dịch hợp lệ (tối đa 40 ký tự)");
        }
        if (orderRepository.existsByMaGiaoDichIgnoreCase(reference)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Mã giao dịch này đã được ghi nhận");
        }

        ThuongMaiOrderResponse previous = toResponse(order);
        order.setPhuongThucThanhToan("VIETQR");
        order.setTrangThaiThanhToan(PAID);
        order.setMaGiaoDich(reference);
        ThuongMaiOrder saved = orderRepository.save(order);
        ThuongMaiOrderResponse response = toResponse(saved);
        activityLogService.record(actor(), "CONFIRM_VIETQR_PAYMENT", "ThuongMaiOrder", saved.getId(),
                previous, response, "Đã đối chiếu thanh toán VietQR đơn mua " + saved.getMaDonHang()
                        + " (mã tham chiếu " + reference + ").");
        return response;
    }

    @Transactional
    public ThuongMaiOrderResponse cancelOrder(Integer orderId) {
        ThuongMaiOrder order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng"));
        ensureOrderAccess(order);
        boolean cancellable = PENDING.equals(order.getTrangThai())
                || PENDING_APPROVAL.equals(order.getTrangThai())
                || DISPOSAL_APPROVAL.equals(order.getTrangThai())
                || ORDERED.equals(order.getTrangThai());
        if (!cancellable) {
            throw badRequest("Chỉ có thể hủy đơn chưa nhận hàng");
        }
        ThuongMaiOrderResponse previous = toResponse(order);
        order.setTrangThai("DA_HUY");
        ThuongMaiOrder saved = orderRepository.save(order);
        ThuongMaiOrderResponse response = toResponse(saved);
        activityLogService.record(actor(), "CANCEL", "ThuongMaiOrder", saved.getId(), previous, response,
                "Đơn hàng " + saved.getMaDonHang() + " đã bị hủy.");
        return response;
    }

    @Transactional
    public ThuongMaiOrderResponse approvePurchaseOrder(Integer orderId) {
        ThuongMaiOrder order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng"));
        if (!"MUA".equals(order.getLoaiDon()) || !PENDING_APPROVAL.equals(order.getTrangThai())) {
            throw badRequest("Đơn mua sắm không ở trạng thái chờ duyệt");
        }
        ThuongMaiOrderResponse previous = toResponse(order);
        order.setTrangThai(ORDERED);
        order.setNguoiDuyet(actor());
        order.setDuyetLuc(java.time.Instant.now());
        ThuongMaiOrder saved = orderRepository.save(order);
        ThuongMaiOrderResponse response = toResponse(saved);
        activityLogService.record(actor(), "APPROVE", "ThuongMaiOrder", saved.getId(), previous, response,
                "Đơn mua sắm " + saved.getMaDonHang() + " đã được duyệt và đặt.");
        return response;
    }

    @Transactional
    public ThuongMaiOrderResponse approveDisposal(Integer orderId) {
        ThuongMaiOrder order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy phiếu thanh lý"));
        if (!"BAN".equals(order.getLoaiDon()) || !DISPOSAL_APPROVAL.equals(order.getTrangThai())) {
            throw badRequest("Phiếu thanh lý không ở trạng thái chờ duyệt");
        }
        List<Integer> ids = order.getChiTiet().stream().map(ThuongMaiOrderItem::getTaiSanId).toList();
        List<TaiSan> lockedAssets = assetRepository.findAllByIdForUpdate(ids);
        if (lockedAssets.size() != ids.size() || lockedAssets.stream()
                .anyMatch(asset -> !isEligibleDisposalStatus(asset.getTinhTrang())
                        || hasActiveAssignmentOrRepair(asset.getTaiSanId()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Có tài sản không còn trong kho để thanh lý");
        }
        ThuongMaiOrderResponse previous = toResponse(order);
        Map<Integer, TaiSan> assetById = new HashMap<>();
        lockedAssets.forEach(asset -> assetById.put(asset.getTaiSanId(), asset));
        LocalDate disposalDate = order.getNgayThanhLy() == null ? LocalDate.now() : order.getNgayThanhLy();
        order.getChiTiet().forEach(item -> item.setGiaTriConLai(
                remainingValue(assetById.get(item.getTaiSanId()), disposalDate)));
        lockedAssets.forEach(asset -> asset.setTinhTrang(RETIRED));
        assetRepository.saveAll(lockedAssets);
        order.setTrangThai(DISPOSED);
        order.setNgayThanhLy(disposalDate);
        order.setNguoiDuyet(actor());
        order.setDuyetLuc(java.time.Instant.now());
        ThuongMaiOrder saved = orderRepository.save(order);
        ThuongMaiDisposalRecord record = new ThuongMaiDisposalRecord();
        record.setOrderId(saved.getId());
        record.setFileName(saved.getMaDonHang() + "-bien-ban-thanh-ly.pdf");
        record.setContent(disposalRecordPdfGenerator.generate(saved));
        disposalRecordRepository.save(record);
        ThuongMaiOrderResponse response = toResponse(saved);
        activityLogService.record(actor(), "APPROVE_DISPOSAL", "ThuongMaiOrder", saved.getId(), previous, response,
                "Phiếu thanh lý " + saved.getMaDonHang() + " đã được duyệt.");
        return response;
    }

    @Transactional
    public ThuongMaiOrderResponse rejectDisposal(Integer orderId, String note) {
        if (note == null || note.isBlank() || note.trim().length() > 1000) {
            throw badRequest("Vui lòng nhập lý do từ chối, tối đa 1.000 ký tự");
        }
        ThuongMaiOrder order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy phiếu thanh lý"));
        if (!"BAN".equals(order.getLoaiDon()) || !DISPOSAL_APPROVAL.equals(order.getTrangThai())) {
            throw badRequest("Phiếu thanh lý không ở trạng thái chờ duyệt");
        }
        ThuongMaiOrderResponse previous = toResponse(order);
        order.setTrangThai("TU_CHOI_THANH_LY");
        order.setGhiChuDuyetThanhLy(note.trim());
        order.setNguoiDuyet(actor());
        order.setDuyetLuc(java.time.Instant.now());
        ThuongMaiOrder saved = orderRepository.save(order);
        ThuongMaiOrderResponse response = toResponse(saved);
        activityLogService.record(actor(), "REJECT_DISPOSAL", "ThuongMaiOrder", saved.getId(), previous, response,
                "Phiếu thanh lý " + saved.getMaDonHang() + " đã bị từ chối.");
        return response;
    }

    @Transactional(readOnly = true)
    public ThuongMaiDisposalRecord disposalRecord(Integer orderId) {
        ThuongMaiOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy phiếu thanh lý"));
        if (!DISPOSED.equals(order.getTrangThai())) {
            throw badRequest("Biên bản chỉ có sau khi phiếu thanh lý được duyệt");
        }
        return disposalRecordRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Biên bản thanh lý chưa được lưu"));
    }

    @Transactional
    public ThuongMaiOrderResponse receivePurchaseOrder(Integer orderId, ThuongMaiReceiptRequest request) {
        ThuongMaiOrder order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng"));
        if (!"MUA".equals(order.getLoaiDon())
                || !(ORDERED.equals(order.getTrangThai()) || PARTIALLY_RECEIVED.equals(order.getTrangThai())
                || (COMPLETE.equals(order.getTrangThai()) && hasOutstandingQuantity(order)))) {
            throw badRequest("Chỉ có thể nhận hàng cho đơn mua sắm đã đặt");
        }
        if (request == null || request.chiTiet() == null || request.chiTiet().size() != order.getChiTiet().size()) {
            throw badRequest("Cần đối chiếu số lượng thực nhận cho tất cả dòng sản phẩm");
        }
        Map<Integer, ThuongMaiReceiptRequest.Item> receivedByItem = new HashMap<>();
        for (ThuongMaiReceiptRequest.Item item : request.chiTiet()) {
            if (item == null || item.chiTietId() == null || item.soLuongThucNhan() == null
                    || receivedByItem.putIfAbsent(item.chiTietId(), item) != null) {
                throw badRequest("Thông tin số lượng thực nhận không hợp lệ");
            }
        }
        if (receivedByItem.size() != order.getChiTiet().size()) {
            throw badRequest("Thông tin số lượng thực nhận không khớp với đơn hàng");
        }
        LocalDate receiptDate = request.ngayNhap() == null ? LocalDate.now() : request.ngayNhap();
        if (receiptDate.isAfter(LocalDate.now())) {
            throw badRequest("Ngày nhập kho không được ở tương lai");
        }
        String notes = request.ghiChu() == null || request.ghiChu().isBlank() ? null : request.ghiChu().trim();
        if (notes != null && notes.length() > 500) {
            throw badRequest("Ghi chú phiếu nhập tối đa 500 ký tự");
        }

        Map<Integer, PreparedReceiptItem> preparedByItem = new HashMap<>();
        Set<String> serialsInReceipt = new HashSet<>();
        int totalReceived = 0;
        for (ThuongMaiOrderItem item : order.getChiTiet()) {
            ThuongMaiReceiptRequest.Item receiptItem = receivedByItem.get(item.getId());
            Integer received = receiptItem == null ? null : receiptItem.soLuongThucNhan();
            int damaged = receiptItem == null || receiptItem.soLuongHong() == null
                    ? 0 : receiptItem.soLuongHong();
            int remaining = item.getSoLuong() - item.getSoLuongDaNhan();
            if (received == null || received < 0 || received > remaining
                    || damaged < 0 || damaged > received) {
                throw badRequest("Số lượng nhận phải từ 0 đến số còn lại; số lượng hỏng không được vượt số thực nhận");
            }

            String itemNotes = receiptItem.ghiChu() == null || receiptItem.ghiChu().isBlank()
                    ? null : receiptItem.ghiChu().trim();
            if (itemNotes != null && itemNotes.length() > 500) {
                throw badRequest("Ghi chú từng dòng nhập tối đa 500 ký tự");
            }
            if ("VAT_TU".equals(item.getLoaiDong())
                    && receiptItem.assetDetails() != null && !receiptItem.assetDetails().isEmpty()) {
                throw badRequest("Không nhập thông tin tài sản riêng cho vật tư tiêu hao");
            }

            List<ThuongMaiReceiptRequest.AssetDetails> submittedDetails =
                    receiptItem.assetDetails() == null ? List.of() : receiptItem.assetDetails();
            if (!submittedDetails.isEmpty() && submittedDetails.size() != received) {
                throw badRequest("Số thông tin tài sản phải khớp số thiết bị thực nhận");
            }
            List<String> serials = receiptItem.serialNumbers() == null ? List.of()
                    : receiptItem.serialNumbers().stream()
                            .filter(value -> value != null && !value.isBlank())
                            .map(String::trim)
                            .toList();
            if ("VAT_TU".equals(item.getLoaiDong()) && !serials.isEmpty()) {
                throw badRequest("Không nhập serial riêng cho vật tư tiêu hao");
            }
            if (!submittedDetails.isEmpty()) {
                List<String> detailSerials = submittedDetails.stream()
                        .map(detail -> detail == null || detail.serialNumber() == null
                                || detail.serialNumber().isBlank() ? null : detail.serialNumber().trim())
                        .toList();
                if (!serials.isEmpty() && !serials.equals(detailSerials)) {
                    throw badRequest("Danh sách serial không khớp thông tin từng tài sản");
                }
                serials = detailSerials.stream().filter(value -> value != null).toList();
            }
            if (submittedDetails.isEmpty() && !serials.isEmpty() && serials.size() != received
                    || serials.stream().anyMatch(value -> value.length() > 100)
                    || serials.stream().map(value -> value.toLowerCase(Locale.ROOT)).distinct().count() != serials.size()) {
                throw badRequest("Nhập serial theo từng thiết bị nhận; số serial phải khớp số thực nhận và tối đa 100 ký tự");
            }
            List<ResolvedAssetDetails> assetDetails = new ArrayList<>();
            if (!"VAT_TU".equals(item.getLoaiDong()) && received > 0) {
                LoaiTaiSan category = categoryRepository.findById(item.getLoaiTaiSanId())
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.CONFLICT, "Danh mục tài sản đặt mua không còn tồn tại"));
                List<String> normalizedSerials = serials;
                List<ThuongMaiReceiptRequest.AssetDetails> details = submittedDetails.isEmpty()
                        ? java.util.stream.IntStream.range(0, received)
                                .mapToObj(index -> new ThuongMaiReceiptRequest.AssetDetails(
                                        normalizedSerials.size() > index ? normalizedSerials.get(index) : null,
                                        null, null, null, null))
                                .toList()
                        : submittedDetails;
                if (category.isRequiresSerial() && details.stream().anyMatch(detail ->
                        detail == null || detail.serialNumber() == null || detail.serialNumber().isBlank())) {
                    throw badRequest("Loại tài sản " + category.getTenLoai() + " yêu cầu serial cho từng thiết bị");
                }
                for (ThuongMaiReceiptRequest.AssetDetails detail : details) {
                    if (detail == null) {
                        throw badRequest("Thông tin từng tài sản không hợp lệ");
                    }
                    String serial = detail.serialNumber() == null || detail.serialNumber().isBlank()
                            ? null : detail.serialNumber().trim();
                    String normalizedSerial = serial == null ? null : serial.toLowerCase(Locale.ROOT);
                    if (normalizedSerial != null && (!serialsInReceipt.add(normalizedSerial)
                            || assetRepository.existsBySerialNumberIgnoreCase(serial))) {
                        throw badRequest("Serial đã tồn tại trong hệ thống hoặc phiếu nhập");
                    }
                    if (detail.brand() != null && detail.brand().trim().length() > 100
                            || detail.model() != null && detail.model().trim().length() > 100) {
                        throw badRequest("Hãng và model tối đa 100 ký tự");
                    }
                    LocalDate warrantyEndDate = detail.warrantyEndDate() == null
                            ? item.getWarrantyEndDate() : detail.warrantyEndDate();
                    if (warrantyEndDate != null && warrantyEndDate.isBefore(receiptDate)) {
                        throw badRequest("Hạn bảo hành không được trước ngày nhập kho");
                    }
                    AdminMasterData location = null;
                    if (detail.locationId() != null) {
                        location = masterRepository.findById(detail.locationId())
                                .filter(row -> "LOCATION".equals(row.getKind()))
                                .orElseThrow(() -> badRequest("Vị trí tài sản không tồn tại"));
                    } else if (!submittedDetails.isEmpty()) {
                        throw badRequest("Vui lòng chọn vị trí cho từng tài sản");
                    }
                    assetDetails.add(new ResolvedAssetDetails(serial,
                            detail.brand() == null || detail.brand().isBlank() ? null : detail.brand().trim(),
                            detail.model() == null || detail.model().isBlank() ? null : detail.model().trim(),
                            warrantyEndDate, location));
                }
            }
            preparedByItem.put(item.getId(),
                    new PreparedReceiptItem(remaining, received, damaged, serials, assetDetails, itemNotes));
            totalReceived += received;
        }
        if (totalReceived == 0) {
            throw badRequest("Số lượng thực nhận phải lớn hơn 0");
        }

        boolean fullyReceived = true;
        ThuongMaiOrderResponse previous = toResponse(order);
        StockReceipt receipt = new StockReceipt();
        receipt.setPurchaseOrderId(order.getId());
        receipt.setOrderNumber(order.getMaDonHang());
        receipt.setSupplierName(order.getSupplierName());
        receipt.setReceiptDate(receiptDate);
        receipt.setReceivedBy(actor());
        receipt.setNotes(notes);
        receiptRepository.saveAndFlush(receipt);
        receipt.setReceiptNumber(String.format(Locale.ROOT, "PNK-%08d", receipt.getId()));

        int sequence = 0;
        for (ThuongMaiOrderItem item : order.getChiTiet()) {
            PreparedReceiptItem prepared = preparedByItem.get(item.getId());
            int received = prepared.received();
            int damaged = prepared.damaged();
            int previouslyReceived = item.getSoLuongDaNhan();
            item.setSoLuongDaNhan(item.getSoLuongDaNhan() + received);
            fullyReceived &= item.getSoLuongDaNhan() >= item.getSoLuong();

            StockReceiptItem savedReceiptItem = new StockReceiptItem();
            savedReceiptItem.setReceipt(receipt);
            savedReceiptItem.setPurchaseOrderItemId(item.getId());
            savedReceiptItem.setProductName(item.getTenSanPham());
            savedReceiptItem.setOrderedQuantity(item.getSoLuong());
            savedReceiptItem.setPreviouslyReceivedQuantity(previouslyReceived);
            savedReceiptItem.setReceivedQuantity(received);
            savedReceiptItem.setDamagedQuantity(damaged);
            savedReceiptItem.setMissingQuantity(prepared.remaining() - received);
            savedReceiptItem.setNotes(prepared.notes());
            receipt.getItems().add(savedReceiptItem);

            if ("VAT_TU".equals(item.getLoaiDong())) {
                Consumable consumable = consumableRepository.findByIdForUpdate(item.getConsumableId())
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.CONFLICT, "Vật tư đặt mua không còn tồn tại"));
                int goodQuantity = received - damaged;
                consumable.setStockQuantity(consumable.getStockQuantity().add(BigDecimal.valueOf(goodQuantity)));
                consumable.setUnitPrice(item.getDonGia());
            } else {
                sequence = createReceivedAssets(
                        order, item, receipt, received, damaged, prepared.assetDetails(), sequence);
            }
        }
        order.setTrangThai(fullyReceived ? RECEIVED : PARTIALLY_RECEIVED);
        order.setNhanHangLuc(java.time.Instant.now());
        if ("TIEN_MAT".equals(order.getPhuongThucThanhToan())) {
            order.setTrangThaiThanhToan(PAID);
        }
        receiptRepository.save(receipt);
        StockReceiptDtos.Response receiptResponse = toReceiptResponse(receipt);
        ThuongMaiOrder saved = orderRepository.save(order);
        ThuongMaiOrderResponse response = toResponse(saved);
        activityLogService.record(actor(), "RECEIVE", "StockReceipt", receipt.getId(), null, receiptResponse,
                "Đã tạo phiếu nhập " + receipt.getReceiptNumber() + " cho đơn " + order.getMaDonHang() + ".");
        activityLogService.record(actor(), "RECEIVE", "ThuongMaiOrder", saved.getId(), previous, response,
                "Đơn mua sắm " + saved.getMaDonHang() + " đã đối chiếu và " +
                        (fullyReceived ? "nhập kho hoàn tất." : "nhận hàng một phần."));
        if (fullyReceived) {
            purchaseRequestRepository.findByPurchaseOrder_Id(saved.getId()).stream()
                    .filter(purchaseRequest -> "APPROVED".equals(purchaseRequest.getStatus()))
                    .forEach(purchaseRequest -> {
                        Map<String, Object> oldRequest = Map.of(
                                "status", purchaseRequest.getStatus(),
                                "purchaseOrderNumber", saved.getMaDonHang());
                        purchaseRequest.setStatus("COMPLETED");
                        purchaseRequestRepository.save(purchaseRequest);
                        activityLogService.record(actor(), "COMPLETE", "PurchaseRequest", purchaseRequest.getId(),
                                oldRequest, Map.of("status", purchaseRequest.getStatus(),
                                        "purchaseOrderNumber", saved.getMaDonHang()),
                                "Yêu cầu mua sắm hoàn tất sau khi đơn đã nhận đủ.");
                    });
        }
        return response;
    }

    private int createReceivedAssets(
            ThuongMaiOrder order,
            ThuongMaiOrderItem item,
            StockReceipt receipt,
            int quantity,
            int damaged,
            List<ResolvedAssetDetails> assetDetails,
            int sequence) {
        if (quantity == 0) return sequence;
        LoaiTaiSan category = categoryRepository.findById(item.getLoaiTaiSanId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "Danh mục tài sản đặt mua không còn tồn tại"));
        for (int index = 0; index < quantity; index++) {
            TaiSan asset = new TaiSan();
            String code;
            do {
                code = String.format(Locale.ROOT, "DH%07d-%04d", order.getId(), sequence++);
            } while (assetRepository.existsByMaTaiSanIgnoreCase(code));
            asset.setMaTaiSan(code);
            asset.setTenTaiSan(item.getTenSanPham());
            asset.setGiaMua(item.getDonGia());
            asset.setNgayMua(receipt.getReceiptDate());
            ResolvedAssetDetails details = assetDetails.get(index);
            asset.setToaNha(details.location() == null ? item.getToaNha() : details.location().getBuilding());
            asset.setTang(details.location() == null ? item.getTang() : details.location().getFloor());
            asset.setPhong(details.location() == null ? item.getPhong() : details.location().getRoom());
            asset.setWarrantyEndDate(details.warrantyEndDate());
            asset.setSerialNumber(details.serialNumber());
            asset.setBrand(details.brand());
            asset.setModel(details.model());
            asset.setSupplierId(order.getSupplierId());
            asset.setTinhTrang(index < damaged ? "Bảo trì" : IN_STOCK);
            asset.setNotes(index < damaged
                    ? "Hàng hỏng khi giao, ghi nhận tại phiếu " + receipt.getReceiptNumber()
                    : "Nhập kho từ phiếu " + receipt.getReceiptNumber());
            asset.setLoaiTaiSan(category);
            assetRepository.save(asset);
        }
        return sequence;
    }

    private StockReceiptDtos.Response toReceiptResponse(StockReceipt receipt) {
        return new StockReceiptDtos.Response(
                receipt.getId(),
                receipt.getReceiptNumber(),
                receipt.getPurchaseOrderId(),
                receipt.getOrderNumber(),
                receipt.getSupplierName(),
                receipt.getReceiptDate(),
                receipt.getReceivedBy(),
                receipt.getNotes(),
                receipt.getCreatedAt(),
                receipt.getItems().stream().map(item -> new StockReceiptDtos.Item(
                        item.getPurchaseOrderItemId(),
                        item.getProductName(),
                        item.getOrderedQuantity(),
                        item.getPreviouslyReceivedQuantity(),
                        item.getReceivedQuantity(),
                        item.getDamagedQuantity(),
                        item.getMissingQuantity(),
                        item.getNotes())).toList());
    }

    private List<TaiSan> lockAndValidateSaleAssets(List<ThuongMaiOrderRequest.Item> items) {
        List<Integer> ids = new ArrayList<>();
        Set<Integer> uniqueIds = new HashSet<>();
        for (ThuongMaiOrderRequest.Item item : items) {
            if (item == null || item.taiSanId() == null || !uniqueIds.add(item.taiSanId())) {
                throw badRequest("Tài sản thanh lý bị thiếu hoặc xuất hiện nhiều lần trong phiếu");
            }
            ids.add(item.taiSanId());
        }
        List<TaiSan> assets = assetRepository.findAllByIdForUpdate(ids);
        if (assets.size() != ids.size()) {
            throw badRequest("Một hoặc nhiều tài sản không còn tồn tại");
        }
        for (TaiSan asset : assets) {
            if (!isEligibleDisposalStatus(asset.getTinhTrang())
                    || orderRepository.isAssetPendingSale(asset.getTaiSanId())
                    || hasActiveAssignmentOrRepair(asset.getTaiSanId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Tài sản " + asset.getMaTaiSan() + " không còn sẵn sàng để thanh lý");
            }
        }
        return assets;
    }

    private ThuongMaiOrderItem createPurchaseItem(ThuongMaiOrderRequest.Item input) {
        if (input == null) {
            throw badRequest("Dòng sản phẩm đặt mua không hợp lệ");
        }
        if (input.soLuong() == null || input.soLuong() < 1 || input.soLuong() > 1000) {
            throw badRequest("Số lượng phải từ 1 đến 1.000");
        }
        if (!isValidAmount(input.donGia())) {
            throw badRequest("Đơn giá phải lớn hơn 0 và không vượt quá 9.999.999.999.999,99 VNĐ");
        }
        ThuongMaiOrderItem item = new ThuongMaiOrderItem();
        String type = input.loaiDong() == null ? "TAI_SAN" : input.loaiDong();
        if ("VAT_TU".equals(type)) {
            if (input.vatTuId() == null) {
                throw badRequest("Vui lòng chọn vật tư tiêu hao");
            }
            Consumable consumable = consumableRepository.findById(input.vatTuId())
                    .orElseThrow(() -> badRequest("Vật tư tiêu hao không tồn tại"));
            item.setLoaiDong("VAT_TU");
            item.setConsumableId(consumable.getId());
            item.setTenSanPham(consumable.getName());
        } else if ("TAI_SAN".equals(type)) {
            if (input.loaiTaiSanId() == null) {
                throw badRequest("Vui lòng chọn loại tài sản cho từng sản phẩm đặt mua");
            }
            if (input.tenSanPham() == null || input.tenSanPham().isBlank()
                    || input.tenSanPham().trim().length() > 150) {
                throw badRequest("Tên sản phẩm bắt buộc và tối đa 150 ký tự");
            }
            LoaiTaiSan category = categoryRepository.findById(input.loaiTaiSanId())
                    .orElseThrow(() -> badRequest("Loại tài sản đặt mua không tồn tại"));
            item.setLoaiDong("TAI_SAN");
            item.setLoaiTaiSanId(category.getLoaiId());
            item.setTenSanPham(input.tenSanPham().trim());
            item.setWarrantyEndDate(input.warrantyEndDate());
            if (input.viTriId() == null) {
                throw badRequest("Vui lòng chọn vị trí nhận từ danh mục");
            }
            AdminMasterData location = masterRepository.findById(input.viTriId())
                    .filter(row -> "LOCATION".equals(row.getKind()))
                    .orElseThrow(() -> badRequest("Vị trí nhận không tồn tại"));
            item.setToaNha(location.getBuilding());
            item.setTang(location.getFloor());
            item.setPhong(location.getRoom());
        } else {
            throw badRequest("Loại dòng sản phẩm đặt mua không hợp lệ");
        }
        item.setSoLuong(input.soLuong());
        item.setDonGia(input.donGia());
        return item;
    }

    private ThuongMaiOrderItem createSaleItem(
            ThuongMaiOrderRequest.Item input,
            Map<Integer, TaiSan> saleAssets,
            LocalDate disposalDate) {
        if (input == null || input.taiSanId() == null) {
            throw badRequest("Vui lòng chọn tài sản thanh lý");
        }
        TaiSan asset = saleAssets.get(input.taiSanId());
        if (asset == null) {
            throw badRequest("Tài sản thanh lý không hợp lệ");
        }
        if (asset.getNgayMua() != null && disposalDate.isBefore(asset.getNgayMua())) {
            throw badRequest("Ngày thanh lý không được trước ngày mua tài sản");
        }
        BigDecimal residualValue = remainingValue(asset, disposalDate);
        BigDecimal disposalValue = input.donGia() == null ? residualValue : input.donGia();
        if (disposalValue.compareTo(BigDecimal.ZERO) < 0 || disposalValue.compareTo(MAX_AMOUNT) > 0
                || disposalValue.scale() > 2) {
            throw badRequest("Giá trị thanh lý phải từ 0 đến 9.999.999.999.999,99 VNĐ");
        }
        ThuongMaiOrderItem item = new ThuongMaiOrderItem();
        item.setTaiSanId(asset.getTaiSanId());
        item.setLoaiTaiSanId(asset.getLoaiTaiSan() == null ? null : asset.getLoaiTaiSan().getLoaiId());
        item.setMaTaiSan(asset.getMaTaiSan());
        item.setTenSanPham(asset.getTenTaiSan());
        item.setSoLuong(1);
        item.setDonGia(disposalValue);
        item.setGiaTriConLai(residualValue);
        item.setToaNha(asset.getToaNha());
        item.setTang(asset.getTang());
        item.setPhong(asset.getPhong());
        return item;
    }

    private void completeOrder(ThuongMaiOrder order) {
        if ("BAN".equals(order.getLoaiDon())) {
            List<Integer> ids = order.getChiTiet().stream().map(ThuongMaiOrderItem::getTaiSanId).toList();
            List<TaiSan> lockedAssets = assetRepository.findAllByIdForUpdate(ids);
            if (lockedAssets.size() != ids.size()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Tài sản trong đơn không còn tồn tại");
            }
            lockedAssets.forEach(asset -> {
                if (!IN_STOCK.equals(asset.getTinhTrang()) && !WAITING_DISPOSAL.equals(asset.getTinhTrang())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Tài sản " + asset.getMaTaiSan() + " không còn trong kho");
                }
                asset.setTinhTrang(RETIRED);
            });
            assetRepository.saveAll(lockedAssets);
        } else {
            int sequence = 0;
            for (ThuongMaiOrderItem item : order.getChiTiet()) {
                LoaiTaiSan category = categoryRepository.findById(item.getLoaiTaiSanId())
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.CONFLICT, "Danh mục tài sản đặt mua không còn tồn tại"));
                for (int index = 0; index < item.getSoLuong(); index++) {
                    TaiSan asset = new TaiSan();
                    String code;
                    do {
                        code = String.format(Locale.ROOT, "DH%07d-%04d", order.getId(), sequence++);
                    } while (assetRepository.existsByMaTaiSanIgnoreCase(code));
                    asset.setMaTaiSan(code);
                    asset.setTenTaiSan(item.getTenSanPham());
                    asset.setGiaMua(item.getDonGia());
                    asset.setNgayMua(LocalDate.now());
                    asset.setToaNha(item.getToaNha());
                    asset.setTang(item.getTang());
                    asset.setPhong(item.getPhong());
                    asset.setTinhTrang(IN_STOCK);
                    asset.setLoaiTaiSan(category);
                    assetRepository.save(asset);
                }
            }
        }
        order.setTrangThai(COMPLETE);
        order.setTrangThaiThanhToan(PAID);
    }

    private ThuongMaiOrderResponse toResponse(ThuongMaiOrder order) {
        List<ThuongMaiOrderResponse.Item> items = order.getChiTiet().stream()
                .map(item -> new ThuongMaiOrderResponse.Item(
                        item.getId(),
                        item.getTaiSanId(),
                        item.getMaTaiSan(),
                        item.getTenSanPham(),
                        item.getSoLuong(),
                        item.getSoLuongDaNhan(),
                        item.getDonGia(),
                        item.getGiaTriConLai(),
                        item.getDonGia().multiply(BigDecimal.valueOf(item.getSoLuong())),
                        item.getToaNha(),
                        item.getTang(),
                        item.getPhong(),
                        item.getLoaiDong(),
                        item.getConsumableId(),
                        item.getLoaiTaiSanId(),
                        item.getWarrantyEndDate()))
                .toList();
        BigDecimal bookValue = order.getChiTiet().stream()
                .map(item -> (item.getGiaTriConLai() == null ? BigDecimal.ZERO : item.getGiaTriConLai())
                        .multiply(BigDecimal.valueOf(item.getSoLuong())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal profitLoss = order.getTongTien().subtract(bookValue);
        return new ThuongMaiOrderResponse(
                order.getId(),
                order.getMaDonHang(),
                order.getLoaiDon(),
                order.getTrangThai(),
                order.getPhuongThucThanhToan(),
                order.getTrangThaiThanhToan(),
                order.getTongTien(),
                order.getTaoLuc(),
                order.getMaGiaoDich(),
                order.getSupplierId(),
                order.getSupplierName(),
                order.getLyDoThanhLy(),
                order.getGhiChuThanhLy(),
                order.getPhuongThucNhanTien(),
                order.getNgayThanhLy(),
                order.getNgayDuKienGiao(),
                order.getNguoiYeuCau(),
                order.getNguoiDuyet(),
                order.getDuyetLuc(),
                order.getNhanHangLuc(),
                profitLoss,
                "BAN".equals(order.getLoaiDon()) && disposalRecordRepository.existsByOrderId(order.getId()),
                order.getGhiChuDuyetThanhLy(),
                items);
    }

    private boolean hasActiveAssignmentOrRepair(Integer assetId) {
        return assignmentRepository.existsByTaiSan_TaiSanIdAndThuHoiLucIsNull(assetId)
                || maintenanceRepository.existsByTaiSan_TaiSanIdAndTrangThaiIn(
                        assetId, List.of("YEU_CAU", "DANG_XU_LY"));
    }

    private boolean isEligibleDisposalStatus(String status) {
        return IN_STOCK.equals(status) || WAITING_DISPOSAL.equals(status) || "Thiếu".equals(status);
    }

    private boolean hasOutstandingQuantity(ThuongMaiOrder order) {
        return order.getChiTiet().stream().anyMatch(item -> item.getSoLuongDaNhan() < item.getSoLuong());
    }

    private boolean isAdmin() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private boolean isValidAmount(BigDecimal amount) {
        return amount != null
                && amount.compareTo(BigDecimal.ZERO) > 0
                && amount.compareTo(MAX_AMOUNT) <= 0
                && amount.scale() <= 2;
    }

    private record PreparedReceiptItem(int remaining, int received, int damaged, List<String> serials,
            List<ResolvedAssetDetails> assetDetails, String notes) {}

    private record ResolvedAssetDetails(String serialNumber, String brand, String model,
            LocalDate warrantyEndDate, AdminMasterData location) {}

    private String actor() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null || authentication.getName() == null ? "system" : authentication.getName();
    }

    private void ensureOrderAccess(ThuongMaiOrder order) {
        if (order.getNguoiYeuCau() == null || order.getNguoiYeuCau().equalsIgnoreCase(actor())) {
            return;
        }
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean admin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        if (!admin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Chỉ người yêu cầu hoặc quản trị viên mới được thao tác phiếu này");
        }
    }
}
