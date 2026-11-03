package com.example.quanlytaisan.service;

import com.example.quanlytaisan.dto.*;
import com.example.quanlytaisan.entity.TaiSan;
import com.example.quanlytaisan.entity.TaiSanCapPhat;
import com.example.quanlytaisan.entity.TaiSanThuHoiBienBan;
import com.example.quanlytaisan.entity.YeuCauBaoTri;
import com.example.quanlytaisan.repository.TaiSanCapPhatRepository;
import com.example.quanlytaisan.repository.TaiSanRepository;
import com.example.quanlytaisan.repository.TaiSanThuHoiBienBanRepository;
import com.example.quanlytaisan.repository.ThuongMaiOrderRepository;
import com.example.quanlytaisan.repository.YeuCauBaoTriRepository;
import com.example.quanlytaisan.repository.AppUserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

@Service
public class TaiSanVongDoiService {
    private static final String STOCK = "Trong kho";
    private static final String IN_USE = "Đang sử dụng";
    private static final String MAINTENANCE = "Bảo trì";
    private static final String WAITING_DISPOSAL = "Chờ thanh lý";
    private static final List<String> ACTIVE_MAINTENANCE = List.of("YEU_CAU", "DANG_XU_LY");
    private static final Set<String> MAINTENANCE_STATUSES = Set.of(
            "YEU_CAU", "DANG_XU_LY", "HOAN_TAT", "KHONG_SUA_DUOC", "DA_HUY");
    private static final Set<String> REPAIR_SEVERITIES = Set.of("NHE", "TRUNG_BINH", "NGHIEM_TRONG");
    private static final Set<String> REPAIR_LOCATIONS = Set.of("NOI_BO", "NHA_CUNG_CAP");
    private static final Set<String> RETURN_REASONS = Set.of("NGHI_VIEC", "CHUYEN_PHONG", "HET_NHU_CAU", "KHAC");
    private static final BigDecimal MAX_COST = new BigDecimal("9999999999999.99");

    private final TaiSanRepository assetRepository;
    private final TaiSanCapPhatRepository assignmentRepository;
    private final YeuCauBaoTriRepository maintenanceRepository;
    private final ThuongMaiOrderRepository orderRepository;
    private final ActivityLogService activityLogService;
    private final AppUserRepository userRepository;
    private final ReturnRecordPdfGenerator returnRecordPdfGenerator;
    private final TaiSanThuHoiBienBanRepository returnRecordRepository;

    public TaiSanVongDoiService(
            TaiSanRepository assetRepository,
            TaiSanCapPhatRepository assignmentRepository,
            YeuCauBaoTriRepository maintenanceRepository,
            ThuongMaiOrderRepository orderRepository,
            ActivityLogService activityLogService,
            AppUserRepository userRepository,
            ReturnRecordPdfGenerator returnRecordPdfGenerator,
            TaiSanThuHoiBienBanRepository returnRecordRepository) {
        this.assetRepository = assetRepository;
        this.assignmentRepository = assignmentRepository;
        this.maintenanceRepository = maintenanceRepository;
        this.orderRepository = orderRepository;
        this.activityLogService = activityLogService;
        this.userRepository = userRepository;
        this.returnRecordPdfGenerator = returnRecordPdfGenerator;
        this.returnRecordRepository = returnRecordRepository;
    }

    @Transactional(readOnly = true)
    public List<TaiSanCapPhatResponse> assignments(boolean activeOnly) {
        List<TaiSanCapPhat> rows = activeOnly
                ? assignmentRepository.findByThuHoiLucIsNullOrderByIdDesc()
                : assignmentRepository.findTop100ByOrderByIdDesc();
        return rows.stream().map(this::toAssignmentResponse).toList();
    }

    @Transactional
    public TaiSanCapPhatResponse assign(TaiSanCapPhatRequest request) {
        if (request == null || request.taiSanId() == null) {
            throw badRequest("Vui lòng chọn tài sản cần bàn giao");
        }
        String recipient = optionalText(request.nguoiNhan(), 100, "Tên người nhận tối đa 100 ký tự");
        String recipientUsername = optionalText(request.usernameNguoiNhan(), 50, "Tên tài khoản tối đa 50 ký tự");
        if (recipientUsername != null && !userRepository.existsByUsernameIgnoreCase(recipientUsername)) {
            throw badRequest("Tài khoản người nhận không tồn tại");
        }
        String department = optionalText(request.phongBan(), 100, "Phòng ban tối đa 100 ký tự");
        if (recipient == null && department == null) {
            throw badRequest("Vui lòng chọn người nhận hoặc phòng ban");
        }
        if (recipient == null) {
            recipient = department;
        }
        String handoverBy = requiredText(request.nguoiBanGiao(), 100,
                "Người bàn giao bắt buộc và tối đa 100 ký tự");
        String receiverSignature = requiredText(request.nguoiKyNhan(), 100,
                "Người ký nhận bắt buộc và tối đa 100 ký tự");
        String note = optionalText(request.ghiChu(), 500, "Ghi chú bàn giao tối đa 500 ký tự");
        String condition = requiredText(request.tinhTrangLucBanGiao(), 300,
                "Tình trạng tài sản lúc bàn giao là bắt buộc và tối đa 300 ký tự");
        if (request.ngayDuKienThuHoi() != null && request.ngayDuKienThuHoi().isBefore(java.time.LocalDate.now())) {
            throw badRequest("Ngày dự kiến thu hồi không được ở quá khứ");
        }
        LocalDate handoverDate = request.ngayBanGiao() == null ? LocalDate.now() : request.ngayBanGiao();
        if (handoverDate.isAfter(LocalDate.now())) {
            throw badRequest("Ngày bàn giao không được ở tương lai");
        }
        TaiSan asset = lockedAsset(request.taiSanId());
        if (!STOCK.equals(asset.getTinhTrang()) || assignmentRepository
                .existsByTaiSan_TaiSanIdAndThuHoiLucIsNull(asset.getTaiSanId())) {
            throw conflict("Chỉ có thể bàn giao tài sản đang ở trạng thái Trong kho và chưa có người nhận");
        }
        if (maintenanceRepository.existsByTaiSan_TaiSanIdAndTrangThaiIn(asset.getTaiSanId(), ACTIVE_MAINTENANCE)
                || orderRepository.isAssetPendingSale(asset.getTaiSanId())) {
            throw conflict("Tài sản đang được bảo trì hoặc nằm trong phiếu thanh lý chờ duyệt");
        }

        TaiSanCapPhat assignment = new TaiSanCapPhat();
        assignment.setTaiSan(asset);
        assignment.setNguoiNhan(recipient);
        assignment.setUsernameNguoiNhan(recipientUsername);
        assignment.setPhongBan(department);
        assignment.setBanGiaoLuc(handoverDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
        assignment.setNguoiBanGiao(handoverBy);
        assignment.setNguoiKyNhan(receiverSignature);
        assignment.setGhiChu(note);
        assignment.setTinhTrangLucBanGiao(condition);
        assignment.setNgayDuKienThuHoi(request.ngayDuKienThuHoi());
        asset.setTinhTrang(IN_USE);
        assetRepository.save(asset);
        TaiSanCapPhat saved = assignmentRepository.save(assignment);
        TaiSanCapPhatResponse response = toAssignmentResponse(saved);
        activityLogService.record(actor(), "CREATE", "TaiSanCapPhat", saved.getId(), null, response,
                "Đã bàn giao tài sản " + asset.getMaTaiSan() + " cho " + recipient + ".");
        return response;
    }

    @Transactional
    public TaiSanCapPhatResponse returnAsset(Integer assignmentId, ThuHoiTaiSanRequest request) {
        TaiSanCapPhat assignment = assignmentRepository.findByIdWithTaiSan(assignmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy lịch sử bàn giao"));
        TaiSan asset = lockedAsset(assignment.getTaiSan().getTaiSanId());
        if (assignment.getThuHoiLuc() != null) {
            throw conflict("Tài sản này đã được thu hồi");
        }
        TaiSanCapPhatResponse previous = toAssignmentResponse(assignment);
        if (!IN_USE.equals(asset.getTinhTrang())) {
            throw conflict("Trạng thái tài sản không khớp với lịch sử cấp phát");
        }
        if (request == null) {
            throw badRequest("Vui lòng cung cấp tình trạng tài sản khi thu hồi");
        }
        String condition = requiredText(request.tinhTrangLucThuHoi(), 300,
                "Tình trạng tài sản lúc thu hồi là bắt buộc và tối đa 300 ký tự");
        String nextAssetStatus = request.tinhTrangSauThuHoi();
        if (nextAssetStatus == null || !Set.of(STOCK, MAINTENANCE, WAITING_DISPOSAL).contains(nextAssetStatus)) {
            throw badRequest("Trạng thái sau thu hồi phải là Trong kho, Bảo trì hoặc Chờ thanh lý");
        }
        if (maintenanceRepository.existsByTaiSan_TaiSanIdAndTrangThaiIn(asset.getTaiSanId(), ACTIVE_MAINTENANCE)) {
            throw conflict("Không thể thu hồi tài sản khi đang có phiếu sửa chữa chưa hoàn tất");
        }
        String note = optionalText(request.ghiChu(), 500, "Ghi chú thu hồi tối đa 500 ký tự");
        String reason = request.lyDoThuHoi();
        if (reason == null || !RETURN_REASONS.contains(reason)) {
            throw badRequest("Lý do thu hồi không hợp lệ");
        }
        String receivedBy = requiredText(request.nguoiNhanLai(), 100,
                "Người nhận lại bắt buộc và tối đa 100 ký tự");
        String handedBackBy = requiredText(request.nguoiGiaoLai(), 100,
                "Người giao lại bắt buộc và tối đa 100 ký tự");
        LocalDate returnDate = request.ngayThuHoi() == null ? LocalDate.now() : request.ngayThuHoi();
        if (returnDate.isAfter(LocalDate.now())) {
            throw badRequest("Ngày thu hồi không được ở tương lai");
        }
        assignment.setThuHoiLuc(returnDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
        assignment.setGhiChuThuHoi(note);
        assignment.setTinhTrangLucThuHoi(condition);
        assignment.setTinhTrangSauThuHoi(nextAssetStatus);
        assignment.setLyDoThuHoi(reason);
        assignment.setNguoiNhanLai(receivedBy);
        assignment.setNguoiGiaoLai(handedBackBy);
        assignment.setCoBienBanThuHoi(true);
        asset.setTinhTrang(nextAssetStatus);
        assetRepository.save(asset);
        TaiSanCapPhat saved = assignmentRepository.save(assignment);
        TaiSanThuHoiBienBan returnRecord = returnRecordRepository
                .findByPhieuBanGiao_Id(assignmentId)
                .orElseGet(TaiSanThuHoiBienBan::new);
        returnRecord.setPhieuBanGiao(saved);
        returnRecord.setNoiDungPdf(returnRecordPdfGenerator.generate(saved, returnDate));
        returnRecordRepository.save(returnRecord);
        TaiSanCapPhatResponse response = toAssignmentResponse(saved);
        activityLogService.record(actor(), "RETURN", "TaiSanCapPhat", saved.getId(), previous, response,
                "Đã thu hồi tài sản " + asset.getMaTaiSan() + " về trạng thái " + nextAssetStatus + ".");
        return response;
    }

    @Transactional(readOnly = true)
    public byte[] returnRecordPdf(Integer assignmentId) {
        return returnRecordRepository.findPdfByAssignmentId(assignmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Biên bản thu hồi chưa được tạo"));
    }

    @Transactional
    public TaiSanCapPhatResponse linkRecipientAccount(Integer assignmentId, String username) {
        String recipientUsername = requiredText(username, 50, "Tên tài khoản bắt buộc và tối đa 50 ký tự");
        if (!userRepository.existsByUsernameIgnoreCase(recipientUsername)) {
            throw badRequest("Tài khoản người nhận không tồn tại");
        }
        TaiSanCapPhat assignment = assignmentRepository.findByIdWithTaiSan(assignmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy lịch sử cấp phát"));
        if (assignment.getThuHoiLuc() != null) {
            throw conflict("Chỉ có thể gắn tài khoản cho phiếu bàn giao đang hiệu lực");
        }
        TaiSanCapPhatResponse previous = toAssignmentResponse(assignment);
        assignment.setUsernameNguoiNhan(recipientUsername);
        TaiSanCapPhat saved = assignmentRepository.save(assignment);
        TaiSanCapPhatResponse response = toAssignmentResponse(saved);
        activityLogService.record(actor(), "LINK_RECIPIENT_ACCOUNT", "TaiSanCapPhat", assignmentId,
                previous, response, null);
        return response;
    }

    @Transactional(readOnly = true)
    public List<YeuCauBaoTriResponse> maintenances(
            boolean activeOnly, String status, String department, LocalDate fromDate, LocalDate toDate) {
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw badRequest("Ngày báo hỏng bắt đầu không được sau ngày kết thúc");
        }
        if (status != null && !status.isBlank() && !MAINTENANCE_STATUSES.contains(status)) {
            throw badRequest("Trạng thái phiếu sửa chữa không hợp lệ");
        }
        List<YeuCauBaoTri> rows = activeOnly
                ? maintenanceRepository.findByTrangThaiInOrderByIdDesc(ACTIVE_MAINTENANCE)
                : maintenanceRepository.findAllByOrderByIdDesc();
        List<Integer> assetIds = rows.stream().map(row -> row.getTaiSan().getTaiSanId()).distinct().toList();
        var holders = assetIds.isEmpty() ? List.<com.example.quanlytaisan.repository.TaiSanHolderInfo>of()
                : assignmentRepository.findCurrentHolders(assetIds);
        var holderByAsset = holders.stream().collect(java.util.stream.Collectors.toMap(
                com.example.quanlytaisan.repository.TaiSanHolderInfo::getTaiSanId, holder -> holder));
        String normalizedDepartment = department == null ? "" : department.trim().toLowerCase(java.util.Locale.ROOT);
        return rows.stream()
                .filter(row -> status == null || status.isBlank() || status.equals(row.getTrangThai()))
                .filter(row -> {
                    LocalDate reportedDate = row.getNgayBaoHong() == null
                            ? row.getTaoLuc().atZone(ZoneId.systemDefault()).toLocalDate()
                            : row.getNgayBaoHong();
                    return (fromDate == null || !reportedDate.isBefore(fromDate))
                            && (toDate == null || !reportedDate.isAfter(toDate));
                })
                .filter(row -> normalizedDepartment.isEmpty()
                        || (row.getPhongBan() != null
                                ? row.getPhongBan().toLowerCase(java.util.Locale.ROOT).contains(normalizedDepartment)
                                : java.util.Optional.ofNullable(holderByAsset.get(row.getTaiSan().getTaiSanId()))
                                        .map(com.example.quanlytaisan.repository.TaiSanHolderInfo::getPhongBan)
                                        .filter(value -> value.toLowerCase(java.util.Locale.ROOT).contains(normalizedDepartment))
                                        .isPresent()))
                .map(row -> toMaintenanceResponse(row, holderByAsset.get(row.getTaiSan().getTaiSanId())))
                .toList();
    }

    @Transactional
    public YeuCauBaoTriResponse createMaintenance(YeuCauBaoTriRequest request) {
        if (request == null || request.taiSanId() == null) {
            throw badRequest("Vui lòng chọn tài sản cần bảo trì");
        }
        String title = requiredText(request.tieuDe(), 150, "Tiêu đề yêu cầu bắt buộc và tối đa 150 ký tự");
        String description = optionalText(request.moTa(), 1000, "Mô tả tối đa 1.000 ký tự");
        BigDecimal cost = validateCost(request.chiPhi());
        String severity = request.mucDo() == null || request.mucDo().isBlank() ? "TRUNG_BINH" : request.mucDo();
        String handlingLocation = request.noiXuLy() == null || request.noiXuLy().isBlank() ? "NOI_BO" : request.noiXuLy();
        String requestType = request.loaiYeuCau() == null || request.loaiYeuCau().isBlank()
                ? "SUA_CHUA" : request.loaiYeuCau();
        if (!REPAIR_SEVERITIES.contains(severity)) throw badRequest("Mức độ hư hỏng không hợp lệ");
        if (!REPAIR_LOCATIONS.contains(handlingLocation)) throw badRequest("Nơi xử lý không hợp lệ");
        if (!Set.of("SUA_CHUA", "BAO_TRI").contains(requestType)) throw badRequest("Loại phiếu không hợp lệ");
        LocalDate reportedDate = request.ngayBaoHong() == null ? LocalDate.now() : request.ngayBaoHong();
        if (reportedDate.isAfter(LocalDate.now())) throw badRequest("Ngày báo hỏng không được ở tương lai");
        TaiSan asset = lockedAsset(request.taiSanId());
        String requester = actor();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean admin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        boolean assignedToRequester = assignmentRepository.existsByUsernameNguoiNhanIgnoreCaseAndTaiSan_TaiSanIdAndThuHoiLucIsNull(
                requester, asset.getTaiSanId())
                || assignmentRepository.existsByNguoiNhanIgnoreCaseAndTaiSan_TaiSanIdAndThuHoiLucIsNull(
                requester, asset.getTaiSanId());
        if (!admin && !assignedToRequester) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chỉ có thể gửi yêu cầu cho tài sản đang được giao cho bạn");
        }
        if ((!STOCK.equals(asset.getTinhTrang()) && !IN_USE.equals(asset.getTinhTrang())
                && !MAINTENANCE.equals(asset.getTinhTrang()))
                || orderRepository.isAssetPendingSale(asset.getTaiSanId())) {
            throw conflict("Không thể tạo phiếu cho tài sản đã thanh lý hoặc đang chờ thanh lý");
        }
        if (maintenanceRepository.existsByTaiSan_TaiSanIdAndTrangThaiIn(asset.getTaiSanId(), ACTIVE_MAINTENANCE)) {
            throw conflict("Tài sản đã có yêu cầu bảo trì đang mở");
        }
        YeuCauBaoTri maintenance = new YeuCauBaoTri();
        maintenance.setTaiSan(asset);
        maintenance.setTieuDe(title);
        maintenance.setMoTa(description);
        maintenance.setNguoiYeuCau(request.nguoiYeuCau() == null || request.nguoiYeuCau().isBlank()
                ? requester : requiredText(request.nguoiYeuCau(), 100, "Người báo hỏng tối đa 100 ký tự"));
        maintenance.setAnhSuCo(imageLink(request.anhSuCo()));
        maintenance.setTrangThai("YEU_CAU");
        maintenance.setNgayBaoHong(reportedDate);
        maintenance.setMucDo(severity);
        maintenance.setNoiXuLy(handlingLocation);
        maintenance.setLoaiYeuCau(requestType);
        maintenance.setNguonTao(optionalText(request.nguonTao(), 30, "Nguồn tạo tối đa 30 ký tự"));
        maintenance.setPhongBan(assignmentRepository.findCurrentHolders(List.of(asset.getTaiSanId())).stream()
                .findFirst().map(com.example.quanlytaisan.repository.TaiSanHolderInfo::getPhongBan).orElse(null));
        String previousAssetStatus = asset.getTinhTrang();
        if (MAINTENANCE.equals(previousAssetStatus)) {
            previousAssetStatus = assignmentRepository
                    .existsByTaiSan_TaiSanIdAndThuHoiLucIsNull(asset.getTaiSanId()) ? IN_USE : STOCK;
        }
        maintenance.setTrangThaiTruocBaoTri(previousAssetStatus);
        maintenance.setChiPhi(cost);
        asset.setTinhTrang(MAINTENANCE);
        assetRepository.save(asset);
        YeuCauBaoTri saved = maintenanceRepository.save(maintenance);
        YeuCauBaoTriResponse response = toMaintenanceResponse(saved);
        activityLogService.record(actor(), "CREATE", "YeuCauBaoTri", saved.getId(), null, response,
                "Đã tạo yêu cầu bảo trì cho tài sản " + asset.getMaTaiSan() + ".");
        return response;
    }

    @Transactional
    public YeuCauBaoTriResponse updateMaintenance(Integer id, CapNhatBaoTriRequest request) {
        if (request == null || request.trangThai() == null || !MAINTENANCE_STATUSES.contains(request.trangThai())) {
            throw badRequest("Trạng thái phiếu sửa chữa không hợp lệ");
        }
        YeuCauBaoTri maintenance = maintenanceRepository.findByIdWithTaiSan(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy phiếu sửa chữa"));
        BigDecimal cost = validateCost(request.chiPhi());
        String previousStatus = maintenance.getTrangThai();
        String nextStatus = request.trangThai();
        if ("YEU_CAU".equals(previousStatus) && !Set.of("DANG_XU_LY", "DA_HUY").contains(nextStatus)
                || "DANG_XU_LY".equals(previousStatus)
                        && !Set.of("HOAN_TAT", "KHONG_SUA_DUOC", "DA_HUY").contains(nextStatus)
                || Set.of("HOAN_TAT", "KHONG_SUA_DUOC", "DA_HUY").contains(previousStatus)) {
            throw conflict("Không thể chuyển phiếu sửa chữa từ " + previousStatus + " sang " + nextStatus);
        }

        YeuCauBaoTriResponse previous = toMaintenanceResponse(maintenance, null);
        TaiSan asset = lockedAsset(maintenance.getTaiSan().getTaiSanId());
        if (!MAINTENANCE.equals(asset.getTinhTrang())) {
            throw conflict("Trạng thái tài sản không khớp với yêu cầu bảo trì");
        }
        maintenance.setChiPhi(cost);
        maintenance.setTrangThai(nextStatus);
        if (Set.of("HOAN_TAT", "KHONG_SUA_DUOC").contains(nextStatus)) {
            String result = requiredText(request.ketQua(), 1000, "Kết quả xử lý bắt buộc và tối đa 1.000 ký tự");
            String performer = requiredText(request.nguoiThucHien(), 100,
                    "Người thực hiện bắt buộc và tối đa 100 ký tự");
            LocalDate completionDate = request.ngayHoanThanh() == null ? LocalDate.now() : request.ngayHoanThanh();
            if (completionDate.isAfter(LocalDate.now())) throw badRequest("Ngày hoàn thành không được ở tương lai");
            if (maintenance.getNgayBaoHong() != null && completionDate.isBefore(maintenance.getNgayBaoHong())) {
                throw badRequest("Ngày hoàn thành không được trước ngày báo hỏng");
            }
            maintenance.setKetQua(result);
            maintenance.setNguoiThucHien(performer);
            maintenance.setNgayHoanThanh(completionDate);
            maintenance.setHoanTatLuc(completionDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
        } else if ("DA_HUY".equals(nextStatus)) {
            maintenance.setHoanTatLuc(Instant.now());
        }
        if (Set.of("HOAN_TAT", "KHONG_SUA_DUOC", "DA_HUY").contains(nextStatus)) {
            String previousAssetStatus = maintenance.getTrangThaiTruocBaoTri();
            if (!STOCK.equals(previousAssetStatus) && !IN_USE.equals(previousAssetStatus)) {
                previousAssetStatus = assignmentRepository
                        .existsByTaiSan_TaiSanIdAndThuHoiLucIsNull(asset.getTaiSanId()) ? IN_USE : STOCK;
            }
            asset.setTinhTrang("KHONG_SUA_DUOC".equals(nextStatus) ? WAITING_DISPOSAL : previousAssetStatus);
            assetRepository.save(asset);
        }
        YeuCauBaoTri saved = maintenanceRepository.save(maintenance);
        YeuCauBaoTriResponse response = toMaintenanceResponse(saved, null);
        activityLogService.record(actor(), "UPDATE", "YeuCauBaoTri", saved.getId(), previous, response,
                "Phiếu sửa chữa tài sản " + asset.getMaTaiSan() + " chuyển sang " + nextStatus + ".");
        return response;
    }

    private TaiSan lockedAsset(Integer id) {
        return assetRepository.findAllByIdForUpdate(List.of(id)).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy tài sản"));
    }

    private String requiredText(String value, int maxLength, String message) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength) throw badRequest(message);
        return value.trim();
    }

    private String optionalText(String value, int maxLength, String message) {
        if (value == null || value.isBlank()) return null;
        if (value.trim().length() > maxLength) throw badRequest(message);
        return value.trim();
    }

    private String imageLink(String value) {
        String link = optionalText(value, 1000, "Liên kết ảnh tối đa 1.000 ký tự");
        if (link == null) return null;
        try {
            URI uri = new URI(link);
            if (!Set.of("http", "https").contains(uri.getScheme() == null ? "" : uri.getScheme().toLowerCase())
                    || uri.getHost() == null) {
                throw badRequest("Liên kết ảnh phải sử dụng HTTP hoặc HTTPS");
            }
        } catch (URISyntaxException exception) {
            throw badRequest("Liên kết ảnh không hợp lệ");
        }
        return link;
    }

    private BigDecimal validateCost(BigDecimal value) {
        BigDecimal cost = value == null ? BigDecimal.ZERO : value;
        if (cost.compareTo(BigDecimal.ZERO) < 0 || cost.compareTo(MAX_COST) > 0) {
            throw badRequest("Chi phí phải từ 0 đến 9.999.999.999.999,99 VNĐ");
        }
        return cost;
    }

    private TaiSanCapPhatResponse toAssignmentResponse(TaiSanCapPhat row) {
        TaiSan asset = row.getTaiSan();
        return new TaiSanCapPhatResponse(row.getId(), asset.getTaiSanId(), asset.getMaTaiSan(),
                asset.getTenTaiSan(), row.getNguoiNhan(), row.getPhongBan(), row.getBanGiaoLuc(),
                row.getThuHoiLuc(), row.getGhiChu(), row.getGhiChuThuHoi(),
                row.getTinhTrangLucBanGiao(), row.getTinhTrangLucThuHoi(), row.getTinhTrangSauThuHoi(),
                row.getNgayDuKienThuHoi(), row.getUsernameNguoiNhan(), row.getNguoiBanGiao(),
                row.getNguoiKyNhan(), asset.getSerialNumber(),
                asset.getLoaiTaiSan() == null ? null : asset.getLoaiTaiSan().getTenLoai(),
                asset.getToaNha(), asset.getTang(), asset.getPhong(),
                row.getLyDoThuHoi(), row.getNguoiNhanLai(), row.getNguoiGiaoLai(),
                row.isCoBienBanThuHoi());
    }

    private YeuCauBaoTriResponse toMaintenanceResponse(YeuCauBaoTri row) {
        return toMaintenanceResponse(row, null);
    }

    private YeuCauBaoTriResponse toMaintenanceResponse(
            YeuCauBaoTri row, com.example.quanlytaisan.repository.TaiSanHolderInfo holder) {
        TaiSan asset = row.getTaiSan();
        return new YeuCauBaoTriResponse(row.getId(), YeuCauBaoTriResponse.ticketNumber(row.getId()),
                asset.getTaiSanId(), asset.getMaTaiSan(),
                asset.getTenTaiSan(), row.getTieuDe(), row.getMoTa(), row.getTrangThai(),
                row.getChiPhi(), row.getTaoLuc(), row.getHoanTatLuc(), row.getNguoiYeuCau(), row.getAnhSuCo(),
                row.getNguoiThucHien(), row.getNgayHoanThanh(),
                row.getLichBaoTri() == null ? null : row.getLichBaoTri().getId(),
                row.getNgayBaoHong(), row.getMucDo(), row.getNoiXuLy(), row.getKetQua(),
                row.getLoaiYeuCau(), row.getNguonTao(),
                row.getPhongBan() != null ? row.getPhongBan() : holder == null ? null : holder.getPhongBan(),
                asset.getLoaiTaiSan() == null ? null : asset.getLoaiTaiSan().getTenLoai());
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private String actor() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null || authentication.getName() == null ? "system" : authentication.getName();
    }
}
