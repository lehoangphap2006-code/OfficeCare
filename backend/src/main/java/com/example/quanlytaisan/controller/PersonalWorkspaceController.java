package com.example.quanlytaisan.controller;

import com.example.quanlytaisan.dto.PurchaseRequestDtos.CreateRequest;
import com.example.quanlytaisan.dto.PurchaseRequestDtos.Response;
import com.example.quanlytaisan.dto.YeuCauBaoTriRequest;
import com.example.quanlytaisan.dto.YeuCauBaoTriResponse;
import com.example.quanlytaisan.entity.*;
import com.example.quanlytaisan.repository.*;
import com.example.quanlytaisan.service.ActivityLogService;
import com.example.quanlytaisan.service.TaiSanVongDoiService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/me")
@PreAuthorize("hasRole('USER')")
public class PersonalWorkspaceController {
    private final TaiSanCapPhatRepository assignmentRepository;
    private final AppUserRepository userRepository;
    private final YeuCauBaoTriRepository maintenanceRepository;
    private final LoaiTaiSanRepository categoryRepository;
    private final PurchaseRequestRepository purchaseRequestRepository;
    private final TaiSanVongDoiService lifecycleService;
    private final ActivityLogService activityLog;

    public PersonalWorkspaceController(TaiSanCapPhatRepository assignmentRepository,
            AppUserRepository userRepository,
            YeuCauBaoTriRepository maintenanceRepository, LoaiTaiSanRepository categoryRepository,
            PurchaseRequestRepository purchaseRequestRepository, TaiSanVongDoiService lifecycleService,
            ActivityLogService activityLog) {
        this.assignmentRepository = assignmentRepository;
        this.userRepository = userRepository;
        this.maintenanceRepository = maintenanceRepository;
        this.categoryRepository = categoryRepository;
        this.purchaseRequestRepository = purchaseRequestRepository;
        this.lifecycleService = lifecycleService;
        this.activityLog = activityLog;
    }

    @GetMapping("/assets")
    @Transactional(readOnly = true)
    public List<MyAsset> assets(Authentication authentication) {
        return myAssignments(authentication.getName()).stream().map(assignment -> {
            TaiSan asset = assignment.getTaiSan();
            List<MaintenanceHistory> history = maintenanceRepository
                    .findByTaiSan_TaiSanIdOrderByIdDesc(asset.getTaiSanId()).stream()
                    .map(row -> new MaintenanceHistory(row.getId(), row.getTieuDe(), row.getMoTa(),
                            row.getTrangThai(), row.getTaoLuc(), row.getHoanTatLuc(), row.getAnhSuCo(),
                            row.getKetQua()))
                    .toList();
            return new MyAsset(asset.getTaiSanId(), asset.getMaTaiSan(), asset.getTenTaiSan(),
                    asset.getSerialNumber(), asset.getBrand(), asset.getModel(), asset.getToaNha(),
                    asset.getTang(), asset.getPhong(), asset.getWarrantyEndDate(), asset.getTinhTrang(),
                    assignment.getBanGiaoLuc(), assignment.getNgayDuKienThuHoi(), assignment.getId(),
                    asset.getLoaiTaiSan() == null ? null : asset.getLoaiTaiSan().getTenLoai(), history);
        }).toList();
    }

    @GetMapping("/categories")
    @Transactional(readOnly = true)
    public List<CategoryOption> categories() {
        return categoryRepository.findAll(org.springframework.data.domain.Sort.by("tenLoai")).stream()
                .map(row -> new CategoryOption(row.getLoaiId(), row.getTenLoai())).toList();
    }

    @GetMapping("/maintenance")
    @Transactional(readOnly = true)
    public List<YeuCauBaoTriResponse> maintenance(Authentication authentication) {
        return maintenanceRepository.findByNguoiYeuCauIgnoreCaseOrderByIdDesc(authentication.getName()).stream()
                .map(row -> new YeuCauBaoTriResponse(row.getId(), YeuCauBaoTriResponse.ticketNumber(row.getId()),
                        row.getTaiSan().getTaiSanId(),
                        row.getTaiSan().getMaTaiSan(), row.getTaiSan().getTenTaiSan(), row.getTieuDe(),
                        row.getMoTa(), row.getTrangThai(), row.getChiPhi(), row.getTaoLuc(),
                        row.getHoanTatLuc(), row.getNguoiYeuCau(), row.getAnhSuCo(),
                        row.getNguoiThucHien(), row.getNgayHoanThanh(),
                        row.getLichBaoTri() == null ? null : row.getLichBaoTri().getId(),
                        row.getNgayBaoHong(), row.getMucDo(), row.getNoiXuLy(), row.getKetQua(),
                        row.getLoaiYeuCau(), row.getNguonTao(), row.getPhongBan(),
                        row.getTaiSan().getLoaiTaiSan() == null ? null : row.getTaiSan().getLoaiTaiSan().getTenLoai()))
                .toList();
    }

    @PostMapping("/maintenance")
    @ResponseStatus(HttpStatus.CREATED)
    public YeuCauBaoTriResponse createMaintenance(@RequestBody PersonalMaintenanceRequest request) {
        if (request == null) throw badRequest("Thông tin yêu cầu bảo trì không hợp lệ");
        return lifecycleService.createMaintenance(new YeuCauBaoTriRequest(request.taiSanId(),
                request.tieuDe(), request.moTa(), null, null, request.anhSuCo(), null, null,
                request.mucDo(), null, "SUA_CHUA", "NHAN_VIEN"));
    }

    @GetMapping("/purchase-requests")
    @Transactional(readOnly = true)
    public List<Response> purchaseRequests(Authentication authentication) {
        return purchaseRequestRepository.findTop100ByRequesterIgnoreCaseOrderByCreatedAtDesc(authentication.getName())
                .stream().map(this::response).toList();
    }

    @PostMapping("/purchase-requests")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Response createPurchaseRequest(@RequestBody CreateRequest request, Authentication authentication) {
        if (request == null || request.assetName() == null || request.assetName().isBlank()
                || request.assetName().trim().length() > 150) {
            throw badRequest("Tên thiết bị là bắt buộc và tối đa 150 ký tự");
        }
        if (request.categoryId() == null || !categoryRepository.existsById(request.categoryId())) {
            throw badRequest("Vui lòng chọn loại tài sản hợp lệ");
        }
        if (request.quantity() == null || request.quantity() < 1 || request.quantity() > 1000) {
            throw badRequest("Số lượng phải từ 1 đến 1.000");
        }
        if (request.reason() == null || request.reason().isBlank() || request.reason().trim().length() > 1000) {
            throw badRequest("Lý do là bắt buộc và tối đa 1.000 ký tự");
        }
        if (request.estimatedAmount() == null || request.estimatedAmount().compareTo(new BigDecimal("1000")) < 0
                || request.estimatedAmount().scale() > 2
                || request.estimatedAmount().compareTo(new BigDecimal("9999999999999.99")) > 0) {
            throw badRequest("Tổng dự toán phải từ 1.000 VNĐ đến 9.999.999.999.999,99 VNĐ; nhập đầy đủ số tiền bằng VNĐ");
        }
        PurchaseRequest row = new PurchaseRequest();
        row.setRequester(authentication.getName());
        row.setAssetName(request.assetName().trim());
        row.setCategoryId(request.categoryId());
        row.setQuantity(request.quantity());
        row.setReason(request.reason().trim());
        row.setEstimatedAmount(request.estimatedAmount());
        Response response = response(purchaseRequestRepository.save(row));
        activityLog.record(authentication.getName(), "CREATE", "PurchaseRequest", response.id(), null, response, null);
        return response;
    }

    @PutMapping("/purchase-requests/{id}/cancel")
    @Transactional
    public Response cancelPurchaseRequest(@PathVariable Integer id, Authentication authentication) {
        PurchaseRequest row = purchaseRequestRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu mua sắm"));
        if (!row.getRequester().equalsIgnoreCase(authentication.getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Bạn chỉ có thể hủy yêu cầu mua sắm của chính mình");
        }
        if (!"PENDING".equals(row.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Chỉ có thể hủy yêu cầu đang chờ duyệt");
        }
        Response previous = response(row);
        row.setStatus("CANCELLED");
        Response updated = response(purchaseRequestRepository.save(row));
        activityLog.record(authentication.getName(), "CANCEL", "PurchaseRequest", id, previous, updated,
                "Nhân viên đã hủy yêu cầu mua sắm khi còn chờ duyệt.");
        return updated;
    }

    private List<TaiSanCapPhat> myAssignments(String username) {
        String employeeName = userRepository.findByUsernameIgnoreCase(username)
                .map(AppUser::getEmployee)
                .map(AdminEmployee::getFullName)
                .orElse(null);
        return assignmentRepository.findByThuHoiLucIsNullOrderByIdDesc().stream()
                .filter(row -> username.equalsIgnoreCase(row.getUsernameNguoiNhan())
                        || (row.getUsernameNguoiNhan() == null
                                && (username.equalsIgnoreCase(row.getNguoiNhan())
                                        || (employeeName != null && employeeName.equalsIgnoreCase(row.getNguoiNhan())))))
                .toList();
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

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    public record PersonalMaintenanceRequest(Integer taiSanId, String tieuDe, String moTa,
            String anhSuCo, String mucDo) {}
    public record CategoryOption(Integer id, String name) {}
    public record MaintenanceHistory(Integer id, String tieuDe, String moTa, String trangThai,
            Instant taoLuc, Instant hoanTatLuc, String anhSuCo, String ketQua) {}
    public record MyAsset(Integer taiSanId, String maTaiSan, String tenTaiSan, String serialNumber,
            String brand, String model, String toaNha, String tang, String phong, LocalDate warrantyEndDate,
            String tinhTrang, Instant ngayNhan, LocalDate ngayDuKienThuHoi, Integer assignmentId, String tenLoaiTaiSan,
            List<MaintenanceHistory> lichSuBaoTri) {}
}
