package com.example.quanlytaisan.service;

import com.example.quanlytaisan.dto.MaintenanceScheduleDtos.CompleteRequest;
import com.example.quanlytaisan.dto.MaintenanceScheduleDtos.Request;
import com.example.quanlytaisan.dto.MaintenanceScheduleDtos.Response;
import com.example.quanlytaisan.dto.YeuCauBaoTriResponse;
import com.example.quanlytaisan.entity.MaintenanceSchedule;
import com.example.quanlytaisan.entity.TaiSan;
import com.example.quanlytaisan.entity.YeuCauBaoTri;
import com.example.quanlytaisan.repository.MaintenanceScheduleRepository;
import com.example.quanlytaisan.repository.TaiSanCapPhatRepository;
import com.example.quanlytaisan.repository.TaiSanRepository;
import com.example.quanlytaisan.repository.ThuongMaiOrderRepository;
import com.example.quanlytaisan.repository.YeuCauBaoTriRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
public class MaintenanceScheduleService {
    private static final String ACTIVE = "ACTIVE";
    private static final String PAUSED = "PAUSED";
    private static final String DELETED = "DELETED";
    private static final String RETIRED = "Đã thanh lý";
    private static final String WAITING_DISPOSAL = "Chờ thanh lý";
    private static final String REPAIR = "Bảo trì";
    private static final BigDecimal MAX_COST = new BigDecimal("9999999999999.99");

    private final MaintenanceScheduleRepository scheduleRepository;
    private final TaiSanRepository assetRepository;
    private final TaiSanCapPhatRepository assignmentRepository;
    private final YeuCauBaoTriRepository maintenanceRepository;
    private final ThuongMaiOrderRepository orderRepository;
    private final ActivityLogService activityLog;

    public MaintenanceScheduleService(MaintenanceScheduleRepository scheduleRepository,
            TaiSanRepository assetRepository, TaiSanCapPhatRepository assignmentRepository,
            YeuCauBaoTriRepository maintenanceRepository, ThuongMaiOrderRepository orderRepository,
            ActivityLogService activityLog) {
        this.scheduleRepository = scheduleRepository;
        this.assetRepository = assetRepository;
        this.assignmentRepository = assignmentRepository;
        this.maintenanceRepository = maintenanceRepository;
        this.orderRepository = orderRepository;
        this.activityLog = activityLog;
    }

    @Transactional(readOnly = true)
    public List<Response> schedules() {
        List<MaintenanceSchedule> schedules = scheduleRepository.findByTrangThaiNotOrderByIdDesc(DELETED);
        List<Integer> assetIds = schedules.stream().map(schedule -> schedule.getTaiSan().getTaiSanId()).distinct().toList();
        var holders = assetIds.isEmpty() ? List.<com.example.quanlytaisan.repository.TaiSanHolderInfo>of()
                : assignmentRepository.findCurrentHolders(assetIds);
        var holderByAsset = holders.stream().collect(java.util.stream.Collectors.toMap(
                com.example.quanlytaisan.repository.TaiSanHolderInfo::getTaiSanId, holder -> holder));
        return schedules.stream().map(schedule -> toResponse(schedule, holderByAsset.get(
                schedule.getTaiSan().getTaiSanId()))).toList();
    }

    @Transactional
    public Response create(Request request) {
        validateRequest(request);
        TaiSan asset = lockedAsset(request.taiSanId());
        ensureNotRetired(asset);
        if (scheduleRepository.existsByTaiSan_TaiSanIdAndTrangThaiIn(asset.getTaiSanId(), List.of(ACTIVE, PAUSED))) {
            throw conflict("Tài sản đã có lịch bảo trì định kỳ chưa xóa");
        }
        MaintenanceSchedule schedule = new MaintenanceSchedule();
        schedule.setTaiSan(asset);
        schedule.setChuKyThang(request.chuKyThang());
        schedule.setNgayBatDau(request.ngayBatDau());
        schedule.setTrangThai(ACTIVE);
        MaintenanceSchedule saved = scheduleRepository.save(schedule);
        Response response = toResponse(saved, currentHolder(asset.getTaiSanId()));
        activityLog.record(actor(), "CREATE", "MaintenanceSchedule", saved.getId(), null, response,
                "Đã tạo lịch bảo trì định kỳ cho tài sản " + asset.getMaTaiSan() + ".");
        return response;
    }

    @Transactional
    public Response update(Integer id, Request request) {
        validateRequest(request);
        MaintenanceSchedule schedule = getForUpdate(id);
        TaiSan asset = lockedAsset(schedule.getTaiSan().getTaiSanId());
        ensureNotRetired(asset);
        if (!asset.getTaiSanId().equals(request.taiSanId())) {
            throw conflict("Không thể đổi tài sản của lịch; hãy tạo lịch mới cho tài sản khác");
        }
        Response previous = toResponse(schedule, currentHolder(asset.getTaiSanId()));
        schedule.setChuKyThang(request.chuKyThang());
        schedule.setNgayBatDau(request.ngayBatDau());
        Response response = toResponse(scheduleRepository.save(schedule), currentHolder(asset.getTaiSanId()));
        activityLog.record(actor(), "UPDATE", "MaintenanceSchedule", id, previous, response,
                "Đã cập nhật lịch bảo trì định kỳ.");
        return response;
    }

    @Transactional
    public Response setPaused(Integer id, boolean paused) {
        MaintenanceSchedule schedule = getForUpdate(id);
        TaiSan asset = lockedAsset(schedule.getTaiSan().getTaiSanId());
        ensureNotRetired(asset);
        if (DELETED.equals(schedule.getTrangThai())) {
            throw conflict("Lịch bảo trì đã xóa không thể cập nhật");
        }
        Response previous = toResponse(schedule, currentHolder(asset.getTaiSanId()));
        schedule.setTrangThai(paused ? PAUSED : ACTIVE);
        Response response = toResponse(scheduleRepository.save(schedule), currentHolder(asset.getTaiSanId()));
        activityLog.record(actor(), paused ? "PAUSE" : "RESUME", "MaintenanceSchedule", id,
                previous, response, null);
        return response;
    }

    @Transactional
    public void delete(Integer id) {
        MaintenanceSchedule schedule = getForUpdate(id);
        Response previous = toResponse(schedule, currentHolder(schedule.getTaiSan().getTaiSanId()));
        schedule.setTrangThai(DELETED);
        scheduleRepository.save(schedule);
        activityLog.record(actor(), "DELETE", "MaintenanceSchedule", id, previous, null,
                "Đã ngừng theo dõi lịch bảo trì; lịch sử các phiếu vẫn được lưu.");
    }

    @Transactional
    public YeuCauBaoTriResponse complete(Integer id, CompleteRequest request) {
        if (request == null) throw badRequest("Vui lòng nhập thông tin phiếu bảo trì");
        String description = requiredText(request.noiDung(), 1000, "Nội dung công việc bắt buộc và tối đa 1000 ký tự");
        String title = description.length() <= 150 ? description : description.substring(0, 147) + "...";
        String performer = requiredText(request.nguoiThucHien(), 100, "Người thực hiện bắt buộc và tối đa 100 ký tự");
        if (request.ngayHoanThanh() == null) throw badRequest("Ngày hoàn thành là bắt buộc");
        if (request.ngayHoanThanh().isAfter(LocalDate.now())) throw badRequest("Ngày hoàn thành không được ở tương lai");
        BigDecimal cost = request.chiPhi() == null ? BigDecimal.ZERO : request.chiPhi();
        if (cost.compareTo(BigDecimal.ZERO) < 0 || cost.compareTo(MAX_COST) > 0) {
            throw badRequest("Chi phí phải từ 0 đến 9.999.999.999.999,99 VNĐ");
        }

        MaintenanceSchedule schedule = getForUpdate(id);
        if (!ACTIVE.equals(schedule.getTrangThai())) throw conflict("Chỉ có thể ghi nhận bảo trì từ lịch đang hoạt động");
        TaiSan asset = lockedAsset(schedule.getTaiSan().getTaiSanId());
        ensureNotRetired(asset);
        if (REPAIR.equals(asset.getTinhTrang())) {
            throw conflict("Tài sản đang sửa chữa; lần bảo trì định kỳ được tạm hoãn");
        }
        if (orderRepository.isAssetPendingSale(asset.getTaiSanId())) {
            throw conflict("Không thể bảo trì tài sản đang nằm trong phiếu thanh lý chờ duyệt");
        }
        if (maintenanceRepository.existsByTaiSan_TaiSanIdAndTrangThaiIn(
                asset.getTaiSanId(), List.of("YEU_CAU", "DANG_XU_LY"))) {
            throw conflict("Tài sản đang có yêu cầu sửa chữa chưa hoàn tất");
        }

        YeuCauBaoTri maintenance = new YeuCauBaoTri();
        maintenance.setTaiSan(asset);
        maintenance.setLichBaoTri(schedule);
        maintenance.setTieuDe(title);
        maintenance.setMoTa(description);
        maintenance.setNguoiYeuCau(actor());
        maintenance.setNguoiThucHien(performer);
        maintenance.setNgayHoanThanh(request.ngayHoanThanh());
        maintenance.setTrangThai("HOAN_TAT");
        maintenance.setNgayBaoHong(request.ngayHoanThanh());
        maintenance.setMucDo("NHE");
        maintenance.setNoiXuLy("NOI_BO");
        maintenance.setKetQua(description);
        maintenance.setLoaiYeuCau("BAO_TRI");
        maintenance.setNguonTao("BAO_TRI_DINH_KY");
        var holder = currentHolder(asset.getTaiSanId());
        maintenance.setPhongBan(holder == null ? null : holder.getPhongBan());
        maintenance.setChiPhi(cost);
        maintenance.setHoanTatLuc(request.ngayHoanThanh().atStartOfDay(ZoneId.systemDefault()).toInstant());
        schedule.setNgayBaoTriGanNhat(request.ngayHoanThanh());
        maintenanceRepository.save(maintenance);
        scheduleRepository.save(schedule);
        YeuCauBaoTriResponse response = toMaintenanceResponse(maintenance);
        activityLog.record(actor(), "CREATE", "YeuCauBaoTri", maintenance.getId(), null, response,
                "Đã ghi nhận bảo trì định kỳ cho tài sản " + asset.getMaTaiSan() + ".");
        return response;
    }

    @Transactional(readOnly = true)
    public List<YeuCauBaoTriResponse> history(Integer id) {
        scheduleRepository.findById(id)
                .orElseThrow(() -> notFound("Không tìm thấy lịch bảo trì"));
        return maintenanceRepository.findByLichBaoTri_IdOrderByIdDesc(id)
                .stream().map(this::toMaintenanceResponse).toList();
    }

    public Response toResponse(MaintenanceSchedule schedule) {
        return toResponse(schedule, currentHolder(schedule.getTaiSan().getTaiSanId()));
    }

    private Response toResponse(MaintenanceSchedule schedule,
            com.example.quanlytaisan.repository.TaiSanHolderInfo holder) {
        TaiSan asset = schedule.getTaiSan();
        LocalDate nextDue = schedule.getNgayBaoTriGanNhat() == null
                ? schedule.getNgayBatDau() : schedule.getNgayBaoTriGanNhat().plusMonths(schedule.getChuKyThang());
        LocalDate today = LocalDate.now();
        String status;
        if (RETIRED.equals(asset.getTinhTrang()) || "Thanh lý".equals(asset.getTinhTrang())) status = "Đã thanh lý";
        else if (WAITING_DISPOSAL.equals(asset.getTinhTrang())) status = "Chờ thanh lý";
        else if (PAUSED.equals(schedule.getTrangThai())) status = "Tạm dừng";
        else if (REPAIR.equals(asset.getTinhTrang())) status = "Tạm hoãn";
        else if (nextDue.isBefore(today)) status = "Quá hạn";
        else if (nextDue.isEqual(today)) status = "Đến hạn";
        else if (!nextDue.isAfter(today.plusDays(30))) status = "Sắp đến hạn";
        else if (schedule.getNgayBaoTriGanNhat() != null) status = "Đã hoàn thành";
        else status = "Chưa đến hạn";
        return new Response(schedule.getId(), asset.getTaiSanId(), asset.getMaTaiSan(), asset.getTenTaiSan(),
                asset.getLoaiTaiSan() == null ? null : asset.getLoaiTaiSan().getTenLoai(),
                asset.getTinhTrang(), holder == null ? null : holder.getNguoiNhan(),
                holder == null ? null : holder.getPhongBan(), schedule.getChuKyThang(),
                schedule.getNgayBatDau(), schedule.getNgayBaoTriGanNhat(), nextDue, status);
    }

    private YeuCauBaoTriResponse toMaintenanceResponse(YeuCauBaoTri row) {
        TaiSan asset = row.getTaiSan();
        return new YeuCauBaoTriResponse(row.getId(), YeuCauBaoTriResponse.ticketNumber(row.getId()),
                asset.getTaiSanId(), asset.getMaTaiSan(),
                asset.getTenTaiSan(), row.getTieuDe(), row.getMoTa(), row.getTrangThai(),
                row.getChiPhi(), row.getTaoLuc(), row.getHoanTatLuc(), row.getNguoiYeuCau(),
                row.getAnhSuCo(), row.getNguoiThucHien(), row.getNgayHoanThanh(),
                row.getLichBaoTri() == null ? null : row.getLichBaoTri().getId(),
                row.getNgayBaoHong(), row.getMucDo(), row.getNoiXuLy(), row.getKetQua(),
                row.getLoaiYeuCau(), row.getNguonTao(), row.getPhongBan(),
                asset.getLoaiTaiSan() == null ? null : asset.getLoaiTaiSan().getTenLoai());
    }

    private void validateRequest(Request request) {
        if (request == null || request.taiSanId() == null) throw badRequest("Vui lòng chọn tài sản");
        if (request.chuKyThang() == null || request.chuKyThang() <= 0) {
            throw badRequest("Chu kỳ bảo trì phải là số nguyên dương tính theo tháng");
        }
        if (request.ngayBatDau() == null) throw badRequest("Ngày bắt đầu là bắt buộc");
    }

    private MaintenanceSchedule getForUpdate(Integer id) {
        return scheduleRepository.findByIdForUpdate(id).orElseThrow(() -> notFound("Không tìm thấy lịch bảo trì"));
    }

    private TaiSan lockedAsset(Integer id) {
        return assetRepository.findAllByIdForUpdate(List.of(id)).stream().findFirst()
                .orElseThrow(() -> notFound("Không tìm thấy tài sản"));
    }

    private com.example.quanlytaisan.repository.TaiSanHolderInfo currentHolder(Integer assetId) {
        return assignmentRepository.findCurrentHolders(List.of(assetId)).stream().findFirst().orElse(null);
    }

    private void ensureNotRetired(TaiSan asset) {
        if (RETIRED.equals(asset.getTinhTrang()) || "Thanh lý".equals(asset.getTinhTrang())
                || WAITING_DISPOSAL.equals(asset.getTinhTrang())) {
            throw conflict("Tài sản đang chờ thanh lý hoặc đã thanh lý không thể có lịch bảo trì");
        }
    }

    private String requiredText(String value, int maxLength, String message) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength) throw badRequest(message);
        return value.trim();
    }

    private String actor() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null || authentication.getName() == null ? "system" : authentication.getName();
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }
}
