package com.example.quanlytaisan.controller;

import com.example.quanlytaisan.dto.TaiSanRequest;
import com.example.quanlytaisan.dto.TaiSanResponse;
import com.example.quanlytaisan.dto.TaiSanStatsResponse;
import com.example.quanlytaisan.entity.LoaiTaiSan;
import com.example.quanlytaisan.entity.TaiSan;
import com.example.quanlytaisan.entity.TaiSanImage;
import com.example.quanlytaisan.repository.LoaiTaiSanRepository;
import com.example.quanlytaisan.repository.TaiSanRepository;
import com.example.quanlytaisan.repository.TaiSanImageRepository;
import com.example.quanlytaisan.repository.AdminMasterDataRepository;
import com.example.quanlytaisan.repository.AdminInvoiceRepository;
import com.example.quanlytaisan.repository.StocktakeItemRepository;
import com.example.quanlytaisan.repository.TaiSanCapPhatRepository;
import com.example.quanlytaisan.repository.ThuongMaiOrderRepository;
import com.example.quanlytaisan.repository.YeuCauBaoTriRepository;
import com.example.quanlytaisan.repository.BaoTriTongHop;
import com.example.quanlytaisan.repository.TaiSanHolderInfo;
import com.example.quanlytaisan.service.ActivityLogService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/tai-san")
@PreAuthorize("hasRole('ADMIN')")
public class TaiSanController {
    private static final Set<String> STATUSES = Set.of(
            "Trong kho", "Đang sử dụng", "Bảo trì", "Chờ thanh lý", "Thanh lý", "Đã thanh lý", "Thiếu");

    private final TaiSanRepository repository;
    private final TaiSanImageRepository imageRepository;
    private final AdminMasterDataRepository adminMasterDataRepository;
    private final AdminInvoiceRepository invoiceRepository;
    private final StocktakeItemRepository stocktakeItemRepository;
    private final LoaiTaiSanRepository loaiTaiSanRepository;
    private final ThuongMaiOrderRepository orderRepository;
    private final TaiSanCapPhatRepository assignmentRepository;
    private final YeuCauBaoTriRepository maintenanceRepository;
    private final ActivityLogService activityLogService;

    public TaiSanController(
            TaiSanRepository repository,
            TaiSanImageRepository imageRepository,
            AdminMasterDataRepository adminMasterDataRepository,
            AdminInvoiceRepository invoiceRepository,
            StocktakeItemRepository stocktakeItemRepository,
            LoaiTaiSanRepository loaiTaiSanRepository,
            ThuongMaiOrderRepository orderRepository,
            TaiSanCapPhatRepository assignmentRepository,
            YeuCauBaoTriRepository maintenanceRepository,
            ActivityLogService activityLogService) {
        this.repository = repository;
        this.imageRepository = imageRepository;
        this.adminMasterDataRepository = adminMasterDataRepository;
        this.invoiceRepository = invoiceRepository;
        this.stocktakeItemRepository = stocktakeItemRepository;
        this.loaiTaiSanRepository = loaiTaiSanRepository;
        this.orderRepository = orderRepository;
        this.assignmentRepository = assignmentRepository;
        this.maintenanceRepository = maintenanceRepository;
        this.activityLogService = activityLogService;
    }

    @PostMapping(value = "/anh", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> uploadImage(@RequestPart("file") MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > 30 * 1024 * 1024) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ảnh phải có dung lượng từ 1 byte đến 30 MB");
        }
        String contentType = file.getContentType();
        String imageType = validImageType(contentType, file);
        if (imageType == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chỉ chấp nhận ảnh PNG, JPEG hoặc WebP hợp lệ");
        }
        try {
            String fileName = file.getOriginalFilename();
            fileName = fileName == null ? "asset-image" : fileName.replace('\\', '/');
            fileName = fileName.substring(fileName.lastIndexOf('/') + 1);
            fileName = fileName.substring(0, Math.min(255, fileName.length()));
            TaiSanImage image = new TaiSanImage();
            image.setId(UUID.randomUUID().toString());
            image.setFileName(fileName.isBlank() ? "asset-image" : fileName);
            image.setContentType(imageType);
            image.setData(file.getBytes());
            imageRepository.save(image);
            return Map.of("imageReference", "/api/tai-san/anh/" + image.getId());
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không đọc được tệp ảnh", exception);
        }
    }

    @GetMapping("/anh/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> getImage(@PathVariable String id) {
        TaiSanImage image = imageRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy ảnh tài sản"));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        org.springframework.http.ContentDisposition.inline()
                                .filename(image.getFileName(), StandardCharsets.UTF_8).build().toString())
                .body(image.getData());
    }

    @DeleteMapping("/anh/{id}")
    public ResponseEntity<Void> deleteImage(@PathVariable String id) {
        if (!imageRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy ảnh tài sản");
        }
        imageRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public Page<TaiSanResponse> danhSach(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String tinhTrang,
            @RequestParam(required = false) Integer loaiId,
            @RequestParam(required = false) String phongBan,
            @RequestParam(required = false) LocalDate muaTuNgay,
            @RequestParam(required = false) LocalDate muaDenNgay,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "8") int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trang phải từ 0 trở lên và kích thước từ 1 đến 100");
        }
        if (tinhTrang != null && !tinhTrang.isBlank() && !STATUSES.contains(tinhTrang)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tình trạng tài sản không hợp lệ");
        }
        if (muaTuNgay != null && muaDenNgay != null && muaTuNgay.isAfter(muaDenNgay)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ngày mua bắt đầu không được sau ngày kết thúc");
        }

        Page<TaiSan> results = repository.search(
                keyword == null || keyword.isBlank() ? null : keyword.trim(),
                tinhTrang == null || tinhTrang.isBlank() ? null : tinhTrang,
                loaiId,
                phongBan == null || phongBan.isBlank() ? null : phongBan.trim(),
                muaTuNgay,
                muaDenNgay,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "taiSanId")));
        List<Integer> ids = results.getContent().stream().map(TaiSan::getTaiSanId).toList();
        Map<Integer, BaoTriTongHop> maintenanceSummaries = ids.isEmpty()
                ? Map.of()
                : maintenanceRepository.summarizeCompletedByTaiSanIds(ids).stream()
                        .collect(Collectors.toMap(BaoTriTongHop::getTaiSanId, Function.identity()));
        Map<Integer, TaiSanHolderInfo> holders = ids.isEmpty()
                ? Map.of()
                : assignmentRepository.findCurrentHolders(ids).stream()
                        .collect(Collectors.toMap(TaiSanHolderInfo::getTaiSanId, Function.identity()));
        return results.map(asset -> toResponse(asset, maintenanceSummaries.get(asset.getTaiSanId()),
                holders.get(asset.getTaiSanId())));
    }

    @GetMapping("/{id}")
    public TaiSanResponse chiTiet(@PathVariable Integer id) {
        TaiSan asset = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy tài sản ID " + id));
        BaoTriTongHop summary = maintenanceRepository.summarizeCompletedByTaiSanIds(List.of(id)).stream()
                .findFirst().orElse(null);
        TaiSanHolderInfo holder = assignmentRepository.findCurrentHolders(List.of(id)).stream().findFirst().orElse(null);
        return toResponse(asset, summary, holder);
    }

    @GetMapping("/thong-ke")
    public TaiSanStatsResponse thongKe() {
        return new TaiSanStatsResponse(
                repository.count(),
                repository.countByTinhTrang("Đang sử dụng"),
                repository.countByTinhTrang("Trong kho"),
                repository.countByTinhTrang("Bảo trì"),
                repository.countByTinhTrang("Chờ thanh lý"),
                repository.countByTinhTrang("Thanh lý") + repository.countByTinhTrang("Đã thanh lý"),
                repository.sumGiaMua());
    }

    @PostMapping
    @Transactional
    public ResponseEntity<TaiSanResponse> them(@RequestBody TaiSanRequest request, Authentication authentication) {
        validate(request, null);
        if (Set.of("Thanh lý", "Đã thanh lý").contains(request.tinhTrang())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Tài sản chỉ chuyển sang trạng thái thanh lý sau khi phiếu được duyệt");
        }
        TaiSan taiSan = new TaiSan();
        applyRequest(taiSan, request);
        TaiSan saved = repository.save(taiSan);
        TaiSanResponse response = toResponse(saved);
        activityLogService.record(authentication.getName(), "CREATE", "TaiSan", saved.getTaiSanId(), null, response,
                "Đã thêm tài sản " + saved.getMaTaiSan() + ".");
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @Transactional
    public TaiSanResponse capNhat(@PathVariable Integer id, @RequestBody TaiSanRequest request, Authentication authentication) {
        TaiSan taiSan = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy tài sản ID " + id));
        TaiSanResponse oldValue = toResponse(taiSan);
        validate(request, id);
        if (Set.of("Thanh lý", "Đã thanh lý").contains(taiSan.getTinhTrang())
                && !taiSan.getTinhTrang().equals(request.tinhTrang())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Không thể đổi trạng thái tài sản đã thanh lý");
        }
        if (assignmentRepository.existsByTaiSan_TaiSanIdAndThuHoiLucIsNull(id)
                && !"Đang sử dụng".equals(request.tinhTrang())
                && !request.tinhTrang().equals(taiSan.getTinhTrang())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Hãy thu hồi tài sản qua mục Bàn giao tài sản trước khi đổi tình trạng");
        }
        if (maintenanceRepository.existsByTaiSan_TaiSanIdAndTrangThaiIn(id, java.util.List.of("YEU_CAU", "DANG_XU_LY"))
                && !"Bảo trì".equals(request.tinhTrang())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Hãy hoàn tất hoặc hủy yêu cầu bảo trì trước khi đổi tình trạng");
        }
        applyRequest(taiSan, request);
        TaiSanResponse response = toResponse(repository.save(taiSan));
        activityLogService.record(authentication.getName(), "UPDATE", "TaiSan", id, oldValue, response,
                "Tài sản " + response.maTaiSan() + " đã được cập nhật.");
        return response;
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> xoa(@PathVariable Integer id, Authentication authentication) {
        if (!repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy tài sản ID " + id);
        }
        if (orderRepository.isAssetPendingSale(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Không thể xóa tài sản đang nằm trong phiếu thanh lý chờ duyệt");
        }
        TaiSan asset = repository.findById(id).orElseThrow();
        if (Set.of("Thanh lý", "Đã thanh lý").contains(asset.getTinhTrang())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Không thể xóa tài sản đã thanh lý");
        }
        TaiSanResponse oldValue = toResponse(asset);
        if (assignmentRepository.existsByTaiSan_TaiSanId(id) || maintenanceRepository.existsByTaiSan_TaiSanId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Không thể xóa tài sản đã có lịch sử cấp phát hoặc bảo trì");
        }
        if (invoiceRepository.existsByPurchaseAssetId(id) || stocktakeItemRepository.existsByAsset_TaiSanId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Không thể xóa tài sản đã liên kết với hóa đơn hoặc kiểm kê");
        }
        repository.deleteById(id);
        activityLogService.record(authentication.getName(), "DELETE", "TaiSan", id, oldValue, null,
                "Tài sản " + asset.getMaTaiSan() + " đã bị xóa.");
        return ResponseEntity.noContent().build();
    }

    private void validate(TaiSanRequest request, Integer currentId) {
        if (request == null || request.maTaiSan() == null || request.maTaiSan().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã tài sản không được để trống");
        }

        if (request.maTaiSan().trim().length() > 30) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã tài sản không được dài quá 30 ký tự");
        }
        if (request.tenTaiSan() == null || request.tenTaiSan().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tên tài sản không được để trống");
        }
        if (request.tenTaiSan().trim().length() > 150) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tên tài sản không được dài quá 150 ký tự");
        }
        if (request.giaMua() != null && request.giaMua().compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Giá mua không được là số âm");
        }
        if (request.ngayMua() != null && request.ngayMua().isAfter(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ngày mua không được ở tương lai");
        }
        validateOptionalText(request.toaNha(), 100, "Tên tòa nhà tối đa 100 ký tự");
        validateOptionalText(request.tang(), 30, "Tầng tối đa 30 ký tự");
        validateOptionalText(request.phong(), 100, "Phòng tối đa 100 ký tự");
        validateOptionalText(request.serialNumber(), 100, "Số serial tối đa 100 ký tự");
        validateOptionalText(request.brand(), 100, "Thương hiệu tối đa 100 ký tự");
        validateOptionalText(request.model(), 100, "Model tối đa 100 ký tự");
        validateOptionalText(request.imageReference(), 500, "Tham chiếu ảnh tối đa 500 ký tự");
        validateOptionalText(request.notes(), 2000, "Ghi chú tối đa 2000 ký tự");
        if (request.tinhTrang() == null || !STATUSES.contains(request.tinhTrang())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tình trạng tài sản không hợp lệ");
        }
        boolean duplicateCode = currentId == null
                ? repository.existsByMaTaiSanIgnoreCase(request.maTaiSan().trim())
                : repository.existsByMaTaiSanIgnoreCaseAndTaiSanIdNot(request.maTaiSan().trim(), currentId);
        if (duplicateCode) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Mã tài sản đã tồn tại");
        }
        if (request.loaiTaiSanId() == null || !loaiTaiSanRepository.existsById(request.loaiTaiSanId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng chọn loại tài sản hợp lệ");
        }
        if (request.supplierId() != null && !adminMasterDataRepository.existsByKindAndId("SUPPLIER", request.supplierId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nhà cung cấp không tồn tại");
        }
        if (request.chuKyBaoTriThang() != null
                && !Set.of(1, 3, 6, 12).contains(request.chuKyBaoTriThang())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chu kỳ bảo trì phải là 1, 3, 6 hoặc 12 tháng");
        }
    }

    private String validImageType(String contentType, MultipartFile file) {
        try {
            byte[] header = file.getBytes();
            if ("image/png".equalsIgnoreCase(contentType) && header.length >= 8
                    && (header[0] & 0xff) == 0x89 && header[1] == 0x50 && header[2] == 0x4e
                    && header[3] == 0x47 && header[4] == 0x0d && header[5] == 0x0a
                    && header[6] == 0x1a && header[7] == 0x0a) return "image/png";
            if ("image/jpeg".equalsIgnoreCase(contentType) && header.length >= 3
                    && (header[0] & 0xff) == 0xff && (header[1] & 0xff) == 0xd8
                    && (header[2] & 0xff) == 0xff) return "image/jpeg";
            if ("image/webp".equalsIgnoreCase(contentType) && header.length >= 12
                    && new String(header, 0, 4, StandardCharsets.US_ASCII).equals("RIFF")
                    && new String(header, 8, 4, StandardCharsets.US_ASCII).equals("WEBP")) return "image/webp";
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không đọc được tệp ảnh", exception);
        }
        return null;
    }

    private void applyRequest(TaiSan taiSan, TaiSanRequest request) {
        LoaiTaiSan loaiTaiSan = loaiTaiSanRepository.findById(request.loaiTaiSanId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Loại tài sản không tồn tại"));
        taiSan.setMaTaiSan(request.maTaiSan().trim());
        taiSan.setTenTaiSan(request.tenTaiSan().trim());
        taiSan.setGiaMua(request.giaMua());
        taiSan.setNgayMua(request.ngayMua());
        taiSan.setToaNha(trimToNull(request.toaNha()));
        taiSan.setTang(trimToNull(request.tang()));
        taiSan.setPhong(trimToNull(request.phong()));
        taiSan.setSerialNumber(trimToNull(request.serialNumber()));
        taiSan.setBrand(trimToNull(request.brand()));
        taiSan.setModel(trimToNull(request.model()));
        taiSan.setSupplierId(request.supplierId());
        taiSan.setWarrantyEndDate(request.warrantyEndDate());
        taiSan.setImageReference(trimToNull(request.imageReference()));
        taiSan.setNotes(trimToNull(request.notes()));
        taiSan.setTinhTrang(request.tinhTrang());
        taiSan.setChuKyBaoTriThang(request.chuKyBaoTriThang() == null ? 12 : request.chuKyBaoTriThang());
        taiSan.setLoaiTaiSan(loaiTaiSan);
    }

    private TaiSanResponse toResponse(TaiSan taiSan, BaoTriTongHop maintenanceSummary, TaiSanHolderInfo holder) {
        LoaiTaiSan loaiTaiSan = taiSan.getLoaiTaiSan();
        java.time.Instant latestMaintenance = maintenanceSummary == null ? null : maintenanceSummary.getBaoTriGanNhat();
        java.time.Instant nextMaintenance = latestMaintenance == null ? null
                : latestMaintenance.atZone(ZoneId.systemDefault())
                        .plusMonths(taiSan.getChuKyBaoTriThang())
                        .toInstant();
        Integer usefulLifeYears = taiSan.getThoiGianKhauHaoNam() != null ? taiSan.getThoiGianKhauHaoNam()
                : loaiTaiSan == null ? 5 : loaiTaiSan.getThoiGianKhauHaoNam();
        BigDecimal remainingValue = calculateRemainingValue(taiSan, usefulLifeYears);
        return new TaiSanResponse(
                taiSan.getTaiSanId(),
                taiSan.getMaTaiSan(),
                taiSan.getTenTaiSan(),
                taiSan.getGiaMua(),
                taiSan.getTinhTrang(),
                loaiTaiSan == null ? null : loaiTaiSan.getLoaiId(),
                loaiTaiSan == null ? null : loaiTaiSan.getTenLoai(),
                taiSan.getChuKyBaoTriThang(),
                latestMaintenance,
                nextMaintenance,
                maintenanceSummary == null || maintenanceSummary.getChiPhiBaoTriTichLuy() == null
                        ? BigDecimal.ZERO : maintenanceSummary.getChiPhiBaoTriTichLuy(),
                taiSan.getNgayMua(), taiSan.getToaNha(), taiSan.getTang(), taiSan.getPhong(),
                usefulLifeYears, remainingValue,
                holder == null ? null : holder.getNguoiNhan(),
                holder == null ? null : holder.getPhongBan(),
                taiSan.getSerialNumber(), taiSan.getBrand(), taiSan.getModel(), taiSan.getSupplierId(),
                taiSan.getWarrantyEndDate(), taiSan.getImageReference(), taiSan.getNotes(),
                taiSan.getSupplierId() == null ? null : adminMasterDataRepository.findById(taiSan.getSupplierId())
                        .map(com.example.quanlytaisan.entity.AdminMasterData::getName).orElse(null));
    }

    private BigDecimal calculateRemainingValue(TaiSan taiSan, Integer usefulLifeYears) {
        if ("Thanh lý".equals(taiSan.getTinhTrang()) || "Đã thanh lý".equals(taiSan.getTinhTrang())) {
            return BigDecimal.ZERO;
        }
        BigDecimal purchaseValue = taiSan.getGiaMua();
        LocalDate purchaseDate = taiSan.getNgayMua();
        if (purchaseValue == null || purchaseDate == null || usefulLifeYears == null || usefulLifeYears < 1) return null;
        long daysElapsed = Math.max(0, ChronoUnit.DAYS.between(purchaseDate, LocalDate.now()));
        long usefulLifeDays = ChronoUnit.DAYS.between(purchaseDate, purchaseDate.plusYears(usefulLifeYears));
        BigDecimal depreciation = purchaseValue.multiply(BigDecimal.valueOf(daysElapsed))
                .divide(BigDecimal.valueOf(usefulLifeDays), 10, RoundingMode.HALF_UP);
        return purchaseValue.subtract(depreciation).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private void validateOptionalText(String value, int maxLength, String message) {
        if (value != null && value.trim().length() > maxLength) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private TaiSanResponse toResponse(TaiSan taiSan) {
        BaoTriTongHop summary = maintenanceRepository
                .summarizeCompletedByTaiSanIds(List.of(taiSan.getTaiSanId()))
                .stream().findFirst().orElse(null);
        TaiSanHolderInfo holder = assignmentRepository.findCurrentHolders(List.of(taiSan.getTaiSanId()))
                .stream().findFirst().orElse(null);
        return toResponse(taiSan, summary, holder);
    }
}
