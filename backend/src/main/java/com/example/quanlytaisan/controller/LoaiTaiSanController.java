package com.example.quanlytaisan.controller;

import com.example.quanlytaisan.dto.LoaiTaiSanRequest;
import com.example.quanlytaisan.dto.LoaiTaiSanResponse;
import com.example.quanlytaisan.entity.LoaiTaiSan;
import com.example.quanlytaisan.repository.LoaiTaiSanRepository;
import com.example.quanlytaisan.repository.PurchaseRequestRepository;
import com.example.quanlytaisan.repository.TaiSanRepository;
import com.example.quanlytaisan.service.ActivityLogService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/loai-tai-san")
@PreAuthorize("hasRole('ADMIN')")
public class LoaiTaiSanController {
    private final LoaiTaiSanRepository loaiRepository;
    private final TaiSanRepository taiSanRepository;
    private final PurchaseRequestRepository purchaseRequestRepository;
    private final ActivityLogService activityLogService;

    public LoaiTaiSanController(
            LoaiTaiSanRepository loaiRepository,
            TaiSanRepository taiSanRepository,
            PurchaseRequestRepository purchaseRequestRepository,
            ActivityLogService activityLogService) {
        this.loaiRepository = loaiRepository;
        this.taiSanRepository = taiSanRepository;
        this.purchaseRequestRepository = purchaseRequestRepository;
        this.activityLogService = activityLogService;
    }

    @GetMapping
    public List<LoaiTaiSanResponse> danhSach() {
        return loaiRepository.findAll(org.springframework.data.domain.Sort.by("tenLoai")).stream()
                .map(loai -> new LoaiTaiSanResponse(
                        loai.getLoaiId(),
                        loai.getTenLoai(),
                        taiSanRepository.countByLoaiTaiSan_LoaiId(loai.getLoaiId()),
                        loai.getThoiGianKhauHaoNam(),
                        loai.isRequiresSerial(),
                        purchaseRequestRepository.countByCategoryId(loai.getLoaiId())))
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public LoaiTaiSan them(@RequestBody LoaiTaiSanRequest request, Authentication authentication) {
        validate(request, null);
        LoaiTaiSan loai = new LoaiTaiSan();
        loai.setTenLoai(request.tenLoai().trim());
        loai.setThoiGianKhauHaoNam(request.thoiGianKhauHaoNam() == null ? 5 : request.thoiGianKhauHaoNam());
        loai.setRequiresSerial(request.requiresSerial());
        LoaiTaiSan saved = loaiRepository.save(loai);
        activityLogService.record(authentication.getName(), "CREATE", "LoaiTaiSan", saved.getLoaiId(), null,
                response(saved), "Đã thêm loại tài sản " + saved.getTenLoai() + ".");
        return saved;
    }

    @PutMapping("/{id}")
    @Transactional
    public LoaiTaiSan capNhat(@PathVariable Integer id, @RequestBody LoaiTaiSanRequest request, Authentication authentication) {
        LoaiTaiSan loai = loaiRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy loại tài sản ID " + id));
        validate(request, id);
        LoaiTaiSanResponse previous = response(loai);
        loai.setTenLoai(request.tenLoai().trim());
        if (request.thoiGianKhauHaoNam() != null) {
            loai.setThoiGianKhauHaoNam(request.thoiGianKhauHaoNam());
        }
        if (request.requiresSerial() != null) {
            loai.setRequiresSerial(request.requiresSerial());
        }
        LoaiTaiSan saved = loaiRepository.save(loai);
        activityLogService.record(authentication.getName(), "UPDATE", "LoaiTaiSan", id, previous, response(saved),
                "Loại tài sản " + saved.getTenLoai() + " đã được cập nhật.");
        return saved;
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void xoa(@PathVariable Integer id,
            @RequestParam(required = false) Integer replacementId,
            Authentication authentication) {
        if (!loaiRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy loại tài sản ID " + id);
        }
        long assetCount = taiSanRepository.countByLoaiTaiSan_LoaiId(id);
        long requestCount = purchaseRequestRepository.countByCategoryId(id);
        LoaiTaiSan replacement = null;
        if (replacementId != null) {
            if (replacementId.equals(id)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Loại thay thế phải khác loại đang xóa");
            }
            replacement = loaiRepository.findById(replacementId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Không tìm thấy loại tài sản thay thế"));
        }
        if (requestCount > 0 && replacement == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Hãy chọn loại thay thế để chuyển các yêu cầu mua sắm trước khi xóa");
        }
        LoaiTaiSan loai = loaiRepository.findById(id).orElseThrow();
        LoaiTaiSanResponse previous = response(loai);
        final LoaiTaiSan replacementCategory = replacement;
        if (assetCount > 0) {
            List<com.example.quanlytaisan.entity.TaiSan> assets = taiSanRepository.findByLoaiTaiSan_LoaiId(id);
            assets.forEach(asset -> asset.setLoaiTaiSan(replacementCategory));
            taiSanRepository.saveAll(assets);
        }
        if (requestCount > 0) {
            var requests = purchaseRequestRepository.findByCategoryId(id);
            requests.forEach(request -> request.setCategoryId(replacementCategory.getLoaiId()));
            purchaseRequestRepository.saveAll(requests);
        }
        loaiRepository.deleteById(id);
        activityLogService.record(authentication.getName(), "DELETE", "LoaiTaiSan", id, previous, null,
                "Loại tài sản " + loai.getTenLoai() + " đã bị xóa."
                        + (replacementCategory == null
                                ? assetCount == 0 ? "" : " " + assetCount + " tài sản đã được bỏ phân loại."
                                : " Các liên kết đã chuyển sang " + replacementCategory.getTenLoai() + "."));
    }

    private void validate(LoaiTaiSanRequest request, Integer currentId) {
        if (request == null || request.tenLoai() == null || request.tenLoai().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tên loại tài sản không được để trống");
        }
        String name = request.tenLoai().trim();
        if (name.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tên loại tài sản không được dài quá 100 ký tự");
        }
        if (request.thoiGianKhauHaoNam() != null
                && (request.thoiGianKhauHaoNam() < 1 || request.thoiGianKhauHaoNam() > 50)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thời gian khấu hao phải từ 1 đến 50 năm");
        }
        boolean duplicate = currentId == null
                ? loaiRepository.existsByTenLoaiIgnoreCase(name)
                : loaiRepository.existsByTenLoaiIgnoreCaseAndLoaiIdNot(name, currentId);
        if (duplicate) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tên loại tài sản đã tồn tại");
        }
    }

    private LoaiTaiSanResponse response(LoaiTaiSan loai) {
        return new LoaiTaiSanResponse(loai.getLoaiId(), loai.getTenLoai(),
                taiSanRepository.countByLoaiTaiSan_LoaiId(loai.getLoaiId()), loai.getThoiGianKhauHaoNam(),
                loai.isRequiresSerial(), purchaseRequestRepository.countByCategoryId(loai.getLoaiId()));
    }
}
