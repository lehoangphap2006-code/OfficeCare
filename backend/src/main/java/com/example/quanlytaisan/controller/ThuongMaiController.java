package com.example.quanlytaisan.controller;

import com.example.quanlytaisan.dto.ThuongMaiOrderRequest;
import com.example.quanlytaisan.dto.ThuongMaiOrderResponse;
import com.example.quanlytaisan.dto.ThuongMaiReceiptRequest;
import com.example.quanlytaisan.dto.ManualPurchasePaymentRequest;
import com.example.quanlytaisan.entity.Consumable;
import com.example.quanlytaisan.entity.TaiSan;
import com.example.quanlytaisan.entity.ThuongMaiDisposalRecord;
import com.example.quanlytaisan.repository.AdminMasterDataRepository;
import com.example.quanlytaisan.repository.ConsumableRepository;
import com.example.quanlytaisan.repository.TaiSanCapPhatRepository;
import com.example.quanlytaisan.repository.TaiSanHolderInfo;
import com.example.quanlytaisan.repository.YeuCauBaoTriRepository;
import com.example.quanlytaisan.service.ThuongMaiService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/thuong-mai")
@PreAuthorize("hasRole('ADMIN')")
public class ThuongMaiController {
    private final ThuongMaiService service;
    private final AdminMasterDataRepository masterRepository;
    private final ConsumableRepository consumableRepository;
    private final TaiSanCapPhatRepository assignmentRepository;
    private final YeuCauBaoTriRepository maintenanceRepository;

    public ThuongMaiController(
            ThuongMaiService service,
            AdminMasterDataRepository masterRepository,
            ConsumableRepository consumableRepository,
            TaiSanCapPhatRepository assignmentRepository,
            YeuCauBaoTriRepository maintenanceRepository) {
        this.service = service;
        this.masterRepository = masterRepository;
        this.consumableRepository = consumableRepository;
        this.assignmentRepository = assignmentRepository;
        this.maintenanceRepository = maintenanceRepository;
    }

    @GetMapping("/tham-chieu")
    public OrderReferences thamChieu() {
        List<SupplierOption> suppliers = masterRepository.findByKindOrderByNameAsc("SUPPLIER").stream()
                .map(row -> new SupplierOption(row.getId(), row.getName()))
                .toList();
        List<LocationOption> locations = masterRepository.findByKindOrderByNameAsc("LOCATION").stream()
                .map(row -> new LocationOption(row.getId(), row.getBuilding(), row.getFloor(), row.getRoom()))
                .toList();
        List<ConsumableOption> consumables = consumableRepository.findAllByOrderByNameAsc().stream()
                .filter(Consumable::isActive)
                .map(row -> new ConsumableOption(row.getId(), row.getName(), row.getUnit(), row.getUnitPrice()))
                .toList();
        return new OrderReferences(suppliers, locations, consumables);
    }

    @GetMapping("/tai-san-ban")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public List<TaiSanBanResponse> taiSanBan() {
        List<TaiSan> assets = service.saleableAssets();
        Map<Integer, TaiSanHolderInfo> holders = assets.isEmpty() ? Map.of()
                : assignmentRepository.findCurrentHolders(assets.stream().map(TaiSan::getTaiSanId).toList())
                        .stream().collect(Collectors.toMap(TaiSanHolderInfo::getTaiSanId, Function.identity()));
        return assets.stream()
                .map(asset -> {
                    TaiSanHolderInfo holder = holders.get(asset.getTaiSanId());
                    boolean inActiveRepair = maintenanceRepository.existsByTaiSan_TaiSanIdAndTrangThaiIn(
                            asset.getTaiSanId(), List.of("YEU_CAU", "DANG_XU_LY"));
                    String unavailableReason = holder != null ? "Tài sản đang được bàn giao"
                            : inActiveRepair ? "Tài sản đang có phiếu sửa chữa mở" : null;
                    return new TaiSanBanResponse(
                        asset.getTaiSanId(),
                        asset.getMaTaiSan(),
                        asset.getTenTaiSan(),
                        asset.getGiaMua(),
                        service.remainingValue(asset),
                        asset.getLoaiTaiSan() == null ? null : asset.getLoaiTaiSan().getTenLoai(),
                        asset.getNgayMua(),
                        asset.getToaNha(),
                        asset.getTang(),
                        asset.getPhong(),
                        asset.getTinhTrang(),
                        holder == null ? null : holder.getNguoiNhan(),
                        holder == null ? null : holder.getPhongBan(),
                        unavailableReason == null,
                        unavailableReason);
                })
                .toList();
    }

    @GetMapping("/don-hang")
    public List<ThuongMaiOrderResponse> donHang() {
        return service.orders();
    }

    @GetMapping("/nhap-kho")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public List<com.example.quanlytaisan.dto.StockReceiptDtos.Response> phieuNhapKho() {
        return service.receipts();
    }

    @PostMapping("/don-hang")
    public ThuongMaiOrderResponse taoDonHang(@RequestBody ThuongMaiOrderRequest request) {
        return service.createOrder(request);
    }

    @PostMapping("/don-hang/{id}/thanh-toan-mo-phong")
    public ThuongMaiOrderResponse thanhToanMoPhong(@PathVariable Integer id) {
        return service.simulateOnlinePayment(id);
    }

    @PostMapping("/don-hang/{id}/xac-nhan-thanh-toan")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ThuongMaiOrderResponse xacNhanThanhToan(
            @PathVariable Integer id,
            @RequestBody ManualPurchasePaymentRequest request) {
        return service.confirmVietQrPayment(id, request);
    }

    @PostMapping("/don-hang/{id}/huy")
    public ThuongMaiOrderResponse huyDonHang(@PathVariable Integer id) {
        return service.cancelOrder(id);
    }

    @PostMapping("/don-hang/{id}/duyet")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ThuongMaiOrderResponse duyetDonHang(@PathVariable Integer id) {
        return service.approvePurchaseOrder(id);
    }

    @PostMapping("/don-hang/{id}/duyet-thanh-ly")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ThuongMaiOrderResponse duyetThanhLy(@PathVariable Integer id) {
        return service.approveDisposal(id);
    }

    @PostMapping("/don-hang/{id}/tu-choi-thanh-ly")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ThuongMaiOrderResponse tuChoiThanhLy(
            @PathVariable Integer id,
            @RequestBody DisposalReviewRequest request) {
        return service.rejectDisposal(id, request.ghiChu());
    }

    @GetMapping("/don-hang/{id}/bien-ban-thanh-ly")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<byte[]> bienBanThanhLy(@PathVariable Integer id) {
        ThuongMaiDisposalRecord record = service.disposalRecord(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + record.getFileName() + "\"")
                .body(record.getContent());
    }

    @PostMapping("/don-hang/{id}/nhan-hang")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ThuongMaiOrderResponse nhanHang(
            @PathVariable Integer id,
            @RequestBody ThuongMaiReceiptRequest request) {
        return service.receivePurchaseOrder(id, request);
    }

    public record OrderReferences(
            List<SupplierOption> suppliers,
            List<LocationOption> locations,
            List<ConsumableOption> vatTu) {}
    public record SupplierOption(Integer id, String tenNhaCungCap) {}
    public record LocationOption(Integer id, String toaNha, String tang, String phong) {}
    public record ConsumableOption(Integer id, String tenVatTu, String donVi, java.math.BigDecimal donGia) {}

    public record TaiSanBanResponse(
            Integer taiSanId,
            String maTaiSan,
            String tenTaiSan,
            java.math.BigDecimal giaMua,
            java.math.BigDecimal giaTriConLai,
            String tenLoaiTaiSan,
            java.time.LocalDate ngayMua,
            String toaNha,
            String tang,
            String phong,
            String tinhTrang,
            String nguoiDangGiu,
            String phongBanDangGiu,
            boolean coTheThanhLy,
            String lyDoKhongTheThanhLy) {
    }

    public record DisposalReviewRequest(String ghiChu) {}
}
