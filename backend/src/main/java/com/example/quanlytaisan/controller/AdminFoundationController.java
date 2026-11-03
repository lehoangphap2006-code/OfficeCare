package com.example.quanlytaisan.controller;

import com.example.quanlytaisan.dto.AdminDtos.*;
import com.example.quanlytaisan.dto.AdminDtos;
import com.example.quanlytaisan.entity.*;
import com.example.quanlytaisan.repository.*;
import com.example.quanlytaisan.dto.AdminDtos.ConsumableMovementResponse;
import com.example.quanlytaisan.repository.BaoTriTongHop;
import com.example.quanlytaisan.service.ActivityLogService;
import com.example.quanlytaisan.service.StocktakePdfGenerator;
import com.example.quanlytaisan.service.MaintenanceScheduleService;
import org.springframework.data.domain.Sort;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminFoundationController {
    private static final String DEPARTMENT = "DEPARTMENT";
    private static final String LOCATION = "LOCATION";
    private static final String SUPPLIER = "SUPPLIER";
    private static final Set<String> STOCKTAKE_STATUSES = Set.of("DRAFT", "OPEN", "COMPLETED", "CANCELLED");
    private static final Set<String> ITEM_STATUSES = Set.of("CO_MAT", "THIEU", "HONG", "SAI_VI_TRI", "PRESENT", "MISSING", "DAMAGED", "WRONG_LOCATION");

    private final AdminMasterDataRepository masterRepository;
    private final AdminEmployeeRepository employeeRepository;
    private final AppUserRepository userRepository;
    private final ConsumableRepository consumableRepository;
    private final ConsumableIssueRepository issueRepository;
    private final ConsumableReceiptRepository receiptRepository;
    private final ConsumableRequestRepository consumableRequestRepository;
    private final AdminInvoiceRepository invoiceRepository;
    private final AdminInvoiceAttachmentRepository invoiceAttachmentRepository;
    private final AdminInvoiceLineRepository invoiceLineRepository;
    private final AdminInvoicePaymentRepository invoicePaymentRepository;
    private final AdminInvoiceAdjustmentRepository invoiceAdjustmentRepository;
    private final StocktakeSessionRepository stocktakeRepository;
    private final StocktakeItemRepository stocktakeItemRepository;
    private final StocktakeExtraRepository stocktakeExtraRepository;
    private final TaiSanRepository assetRepository;
    private final TaiSanCapPhatRepository assignmentRepository;
    private final YeuCauBaoTriRepository maintenanceRepository;
    private final ActivityLogService activityLog;
    private final StocktakePdfGenerator stocktakePdfGenerator;
    private final MaintenanceScheduleService maintenanceScheduleService;
    private final ThuongMaiOrderRepository commerceOrderRepository;

    public AdminFoundationController(AdminMasterDataRepository masterRepository,
            AdminEmployeeRepository employeeRepository, AppUserRepository userRepository,
            ConsumableRepository consumableRepository,
            ConsumableIssueRepository issueRepository, ConsumableReceiptRepository receiptRepository,
            ConsumableRequestRepository consumableRequestRepository, AdminInvoiceRepository invoiceRepository,
            AdminInvoiceAttachmentRepository invoiceAttachmentRepository,
            AdminInvoiceLineRepository invoiceLineRepository, AdminInvoicePaymentRepository invoicePaymentRepository,
            AdminInvoiceAdjustmentRepository invoiceAdjustmentRepository,
            StocktakeSessionRepository stocktakeRepository, StocktakeItemRepository stocktakeItemRepository,
            StocktakeExtraRepository stocktakeExtraRepository,
            TaiSanRepository assetRepository, TaiSanCapPhatRepository assignmentRepository,
            YeuCauBaoTriRepository maintenanceRepository,
            ActivityLogService activityLog, StocktakePdfGenerator stocktakePdfGenerator,
            MaintenanceScheduleService maintenanceScheduleService, ThuongMaiOrderRepository commerceOrderRepository) {
        this.masterRepository = masterRepository;
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
        this.consumableRepository = consumableRepository;
        this.issueRepository = issueRepository;
        this.receiptRepository = receiptRepository;
        this.consumableRequestRepository = consumableRequestRepository;
        this.invoiceRepository = invoiceRepository;
        this.invoiceAttachmentRepository = invoiceAttachmentRepository;
        this.invoiceLineRepository = invoiceLineRepository;
        this.invoicePaymentRepository = invoicePaymentRepository;
        this.invoiceAdjustmentRepository = invoiceAdjustmentRepository;
        this.stocktakeRepository = stocktakeRepository;
        this.stocktakeItemRepository = stocktakeItemRepository;
        this.stocktakeExtraRepository = stocktakeExtraRepository;
        this.assetRepository = assetRepository;
        this.assignmentRepository = assignmentRepository;
        this.maintenanceRepository = maintenanceRepository;
        this.activityLog = activityLog;
        this.stocktakePdfGenerator = stocktakePdfGenerator;
        this.maintenanceScheduleService = maintenanceScheduleService;
        this.commerceOrderRepository = commerceOrderRepository;
    }

    @GetMapping("/nhan-vien")
    @Transactional(readOnly = true)
    public List<EmployeeResponse> employees() {
        return employeeRepository.findAll(Sort.by("fullName")).stream().map(this::employeeResponse).toList();
    }

    @PostMapping("/nhan-vien")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public EmployeeResponse createEmployee(@RequestBody EmployeeRequest request, Authentication auth) {
        validateEmployee(request, null);
        AdminEmployee row = new AdminEmployee();
        applyEmployee(row, request);
        EmployeeResponse response = employeeResponse(employeeRepository.save(row));
        activityLog.record(auth.getName(), "CREATE", "AdminEmployee", response.id(), null, response, null);
        return response;
    }

    @PutMapping("/nhan-vien/{id}")
    @Transactional
    public EmployeeResponse updateEmployee(@PathVariable Integer id, @RequestBody EmployeeRequest request, Authentication auth) {
        AdminEmployee row = employeeRepository.findById(id).orElseThrow(() -> notFound("Không tìm thấy nhân viên"));
        EmployeeResponse old = employeeResponse(row);
        validateEmployee(request, id);
        applyEmployee(row, request);
        EmployeeResponse response = employeeResponse(employeeRepository.save(row));
        String recoveryAlert = null;
        if (isInactive(response.trangThai()) && !isInactive(old.trangThai())) {
            List<TaiSanCapPhat> assignments = assignmentsForEmployee(response);
            if (!assignments.isEmpty()) {
                recoveryAlert = "Nhân viên " + response.hoTen() + " đã nghỉ việc; có " + assignments.size()
                        + " tài sản đang giao cần lập phiếu thu hồi.";
            }
        }
        activityLog.record(auth.getName(), "UPDATE", "AdminEmployee", id, old, response, recoveryAlert);
        return response;
    }

    @DeleteMapping("/nhan-vien/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void deleteEmployee(@PathVariable Integer id, Authentication auth) {
        AdminEmployee row = employeeRepository.findById(id).orElseThrow(() -> notFound("Không tìm thấy nhân viên"));
        if (userRepository.findAll().stream().anyMatch(user -> user.getEmployee() != null
                && user.getEmployee().getId().equals(id))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Không thể xóa nhân viên đang liên kết với tài khoản");
        }
        EmployeeResponse old = employeeResponse(row);
        employeeRepository.delete(row);
        activityLog.record(auth.getName(), "DELETE", "AdminEmployee", id, old, null, null);
    }

    @GetMapping("/phong-ban")
    @Transactional(readOnly = true)
    public List<DepartmentResponse> departments() {
        return masterRepository.findByKindOrderByNameAsc(DEPARTMENT).stream()
                .map(row -> new DepartmentResponse(row.getId(), row.getCode(), row.getName())).toList();
    }

    @PostMapping("/phong-ban")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public DepartmentResponse createDepartment(@RequestBody DepartmentRequest request, Authentication auth) {
        validateCodeName(request == null ? null : request.maPhongBan(), request == null ? null : request.tenPhongBan());
        ensureMasterUnique(DEPARTMENT, request.maPhongBan(), null);
        AdminMasterData row = new AdminMasterData();
        row.setKind(DEPARTMENT); row.setCode(request.maPhongBan().trim()); row.setName(request.tenPhongBan().trim());
        AdminMasterData saved = masterRepository.save(row);
        DepartmentResponse response = departmentResponse(saved);
        activityLog.record(auth.getName(), "CREATE", "Department", saved.getId(), null, response, null);
        return response;
    }

    @PutMapping("/phong-ban/{id}")
    @Transactional
    public DepartmentResponse updateDepartment(@PathVariable Integer id, @RequestBody DepartmentRequest request, Authentication auth) {
        AdminMasterData row = master(DEPARTMENT, id);
        DepartmentResponse old = departmentResponse(row);
        validateCodeName(request == null ? null : request.maPhongBan(), request == null ? null : request.tenPhongBan());
        ensureMasterUnique(DEPARTMENT, request.maPhongBan(), id);
        row.setCode(request.maPhongBan().trim()); row.setName(request.tenPhongBan().trim());
        DepartmentResponse response = departmentResponse(masterRepository.save(row));
        activityLog.record(auth.getName(), "UPDATE", "Department", id, old, response, null);
        return response;
    }

    @DeleteMapping("/phong-ban/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void deleteDepartment(@PathVariable Integer id, Authentication auth) {
        AdminMasterData row = master(DEPARTMENT, id);
        DepartmentResponse old = departmentResponse(row);
        if (employeeRepository.findAll().stream().anyMatch(e -> row.getName().equalsIgnoreCase(
                Optional.ofNullable(e.getDepartmentId()).map(this::departmentName).orElse("")))) {
            throw conflict("Không thể xóa phòng ban đang được nhân viên sử dụng");
        }
        if (issueRepository.findAll().stream().anyMatch(i -> Objects.equals(i.getDepartmentId(), id))) {
            throw conflict("Không thể xóa phòng ban đang được phiếu xuất sử dụng");
        }
        masterRepository.delete(row);
        activityLog.record(auth.getName(), "DELETE", "Department", id, old, null, null);
    }

    @GetMapping("/vi-tri")
    @Transactional(readOnly = true)
    public List<LocationResponse> locations() {
        return masterRepository.findByKindOrderByNameAsc(LOCATION).stream().map(this::locationResponse).toList();
    }

    @PostMapping("/vi-tri")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public LocationResponse createLocation(@RequestBody LocationRequest request, Authentication auth) {
        validateLocation(request);
        ensureMasterUnique(LOCATION, locationKey(request), null);
        AdminMasterData row = new AdminMasterData(); applyLocation(row, request);
        AdminMasterData saved = masterRepository.save(row);
        LocationResponse response = locationResponse(saved);
        activityLog.record(auth.getName(), "CREATE", "Location", saved.getId(), null, response, null);
        return response;
    }

    @PutMapping("/vi-tri/{id}")
    @Transactional
    public LocationResponse updateLocation(@PathVariable Integer id, @RequestBody LocationRequest request, Authentication auth) {
        AdminMasterData row = master(LOCATION, id);
        LocationResponse old = locationResponse(row);
        validateLocation(request); ensureMasterUnique(LOCATION, locationKey(request), id); applyLocation(row, request);
        LocationResponse response = locationResponse(masterRepository.save(row));
        activityLog.record(auth.getName(), "UPDATE", "Location", id, old, response, null);
        return response;
    }

    @DeleteMapping("/vi-tri/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void deleteLocation(@PathVariable Integer id, Authentication auth) {
        AdminMasterData row = master(LOCATION, id);
        LocationResponse old = locationResponse(row);
        masterRepository.delete(row);
        activityLog.record(auth.getName(), "DELETE", "Location", id, old, null, null);
    }

    @GetMapping("/nha-cung-cap")
    @Transactional(readOnly = true)
    public List<SupplierResponse> suppliers() {
        return masterRepository.findByKindOrderByNameAsc(SUPPLIER).stream().map(this::supplierResponse).toList();
    }

    @PostMapping("/nha-cung-cap")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public SupplierResponse createSupplier(@RequestBody SupplierRequest request, Authentication auth) {
        validateSupplier(request);
        ensureMasterUnique(SUPPLIER, request.tenNhaCungCap(), null);
        AdminMasterData row = new AdminMasterData(); applySupplier(row, request);
        AdminMasterData saved = masterRepository.save(row);
        SupplierResponse response = supplierResponse(saved);
        activityLog.record(auth.getName(), "CREATE", "Supplier", saved.getId(), null, response, null);
        return response;
    }

    @PutMapping("/nha-cung-cap/{id}")
    @Transactional
    public SupplierResponse updateSupplier(@PathVariable Integer id, @RequestBody SupplierRequest request, Authentication auth) {
        AdminMasterData row = master(SUPPLIER, id);
        SupplierResponse old = supplierResponse(row);
        validateSupplier(request); applySupplier(row, request);
        ensureMasterUnique(SUPPLIER, request.tenNhaCungCap(), id);
        SupplierResponse response = supplierResponse(masterRepository.save(row));
        activityLog.record(auth.getName(), "UPDATE", "Supplier", id, old, response, null);
        return response;
    }

    @DeleteMapping("/nha-cung-cap/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void deleteSupplier(@PathVariable Integer id, Authentication auth) {
        AdminMasterData row = master(SUPPLIER, id);
        if (assetRepository.findAll().stream().anyMatch(a -> Objects.equals(a.getSupplierId(), id))
                || invoiceRepository.findAll().stream().anyMatch(i -> Objects.equals(i.getSupplierId(), id))) {
            throw conflict("Không thể xóa nhà cung cấp đang được tham chiếu");
        }
        SupplierResponse old = supplierResponse(row);
        masterRepository.delete(row);
        activityLog.record(auth.getName(), "DELETE", "Supplier", id, old, null, null);
    }

    @GetMapping("/vat-tu")
    @Transactional(readOnly = true)
    public List<ConsumableResponse> consumables() {
        return consumableRepository.findAll(Sort.by("name")).stream().map(this::consumableResponse).toList();
    }

    @PostMapping("/vat-tu")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public ConsumableResponse createConsumable(@RequestBody AdminDtos.ConsumableRequest request, Authentication auth) {
        validateConsumable(request, null);
        Consumable row = new Consumable(); applyConsumable(row, request);
        ConsumableResponse response = consumableResponse(consumableRepository.save(row));
        activityLog.record(auth.getName(), "CREATE", "Consumable", response.id(), null, response, null);
        return response;
    }

    @PutMapping("/vat-tu/{id}")
    @Transactional
    public ConsumableResponse updateConsumable(@PathVariable Integer id, @RequestBody AdminDtos.ConsumableRequest request, Authentication auth) {
        Consumable row = consumableRepository.findByIdForUpdate(id).orElseThrow(() -> notFound("Không tìm thấy vật tư"));
        ConsumableResponse old = consumableResponse(row);
        validateConsumable(request, id); applyConsumable(row, request);
        ConsumableResponse response = consumableResponse(consumableRepository.save(row));
        activityLog.record(auth.getName(), "UPDATE", "Consumable", id, old, response, null);
        return response;
    }

    @DeleteMapping("/vat-tu/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void deleteConsumable(@PathVariable Integer id, Authentication auth) {
        Consumable row = consumableRepository.findByIdForUpdate(id).orElseThrow(() -> notFound("Không tìm thấy vật tư"));
        ConsumableResponse old = consumableResponse(row);
        row.setActive(false);
        ConsumableResponse response = consumableResponse(consumableRepository.save(row));
        activityLog.record(auth.getName(), "DEACTIVATE", "Consumable", id, old, response, null);
    }

    @GetMapping("/vat-tu/phieu-xuat")
    @Transactional(readOnly = true)
    public List<IssueResponse> issues() {
        return issueRepository.findAll(Sort.by(Sort.Direction.DESC, "issuedAt")).stream().map(this::issueResponse).toList();
    }

    @PostMapping("/vat-tu/phieu-xuat")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public IssueResponse issueConsumable(@RequestBody IssueRequest request, Authentication auth) {
        if (request == null || request.vatTuId() == null || request.soLuong() == null
                || request.soLuong().compareTo(BigDecimal.ZERO) <= 0 || request.soLuong().scale() > 2
                || request.soLuong().compareTo(new BigDecimal("999999999999.99")) > 0) {
            throw bad("Số lượng xuất phải lớn hơn 0");
        }
        if (request.ngayXuat() != null && request.ngayXuat().isAfter(LocalDate.now())) throw bad("Ngày xuất không được ở tương lai");
        if (request.nguoiNhan() == null || request.nguoiNhan().isBlank() || request.nguoiNhan().trim().length() > 150) {
            throw bad("Người nhận là bắt buộc và tối đa 150 ký tự");
        }
        String department = request.phongBanId() == null
                ? (request.phongBan() == null || request.phongBan().isBlank() ? null : request.phongBan().trim())
                : departmentName(request.phongBanId());
        if (request.phongBanId() != null && department == null) throw bad("Phòng ban không tồn tại");
        Integer departmentId = request.phongBanId();
        if (departmentId == null && department != null) {
            departmentId = masterRepository.findByKindOrderByNameAsc(DEPARTMENT).stream()
                    .filter(d -> d.getName().equalsIgnoreCase(department) || d.getCode().equalsIgnoreCase(department))
                    .map(AdminMasterData::getId).findFirst().orElse(null);
            if (departmentId == null) throw bad("Phòng ban không tồn tại");
        }
        Consumable item = consumableRepository.findByIdForUpdate(request.vatTuId()).orElseThrow(() -> notFound("Không tìm thấy vật tư"));
        if (!item.isActive()) throw conflict("Vật tư đã ngừng sử dụng");
        if (item.getStockQuantity().compareTo(request.soLuong()) < 0) throw conflict("Tồn kho không đủ để xuất số lượng yêu cầu");
        if (request.ghiChu() != null && request.ghiChu().length() > 500) throw bad("Ghi chú phiếu xuất tối đa 500 ký tự");
        ConsumableIssue slip = new ConsumableIssue();
        slip.setConsumable(item); slip.setDepartmentId(departmentId); slip.setDepartmentName(department);
        slip.setNotes(trim(request.ghiChu()));
        slip.setQuantity(request.soLuong()); slip.setIssuedBy(auth.getName());
        slip.setRecipient(request.nguoiNhan().trim());
        slip.setIssueDate(request.ngayXuat() == null ? LocalDate.now() : request.ngayXuat());
        slip.setIssuedAt(slip.getIssueDate().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant());
        item.setStockQuantity(item.getStockQuantity().subtract(request.soLuong()));
        consumableRepository.save(item);
        IssueResponse response = issueResponse(issueRepository.save(slip), department);
        activityLog.record(auth.getName(), "CREATE", "ConsumableIssue", response.id(), null, response, null);
        return response;
    }

    @PostMapping("/vat-tu/nhap-kho")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public ConsumableReceiptResponse receiveConsumable(@RequestBody ConsumableReceiptRequest request,
            Authentication auth) {
        if (request == null || request.vatTuId() == null || request.soLuong() == null
                || request.soLuong().signum() <= 0 || request.soLuong().scale() > 2
                || request.soLuong().compareTo(new BigDecimal("999999999999.99")) > 0 || request.donGia() == null
                || request.donGia().signum() < 0
                || request.donGia().compareTo(new BigDecimal("999999999999.99")) > 0 || request.ngayNhap() == null) {
            throw bad("Vật tư, số lượng nhập lớn hơn 0, đơn giá không âm và ngày nhập là bắt buộc");
        }
        if (request.ngayNhap().isAfter(LocalDate.now())) throw bad("Ngày nhập không được ở tương lai");
        String receiptNumber = trim(request.soPhieu());
        String purchaseReference = trim(request.donMuaLienQuan());
        if (blank(receiptNumber) && blank(purchaseReference)) throw bad("Cần nhập số phiếu hoặc đơn mua liên quan");
        if (!blank(receiptNumber) && receiptNumber.length() > 50
                || !blank(purchaseReference) && purchaseReference.length() > 50) {
            throw bad("Số phiếu và đơn mua liên quan tối đa 50 ký tự");
        }
        if (request.donGia().scale() > 2 || request.soLuong().scale() > 2) throw bad("Số lượng và đơn giá tối đa 2 chữ số thập phân");
        Consumable item = consumableRepository.findByIdForUpdate(request.vatTuId())
                .orElseThrow(() -> notFound("Không tìm thấy vật tư"));
        if (!item.isActive()) throw conflict("Vật tư đã ngừng sử dụng");
        if (item.getStockQuantity().add(request.soLuong()).compareTo(new BigDecimal("999999999999.99")) > 0) {
            throw bad("Tồn kho vượt quá giới hạn lưu trữ");
        }
        if (request.nhaCungCapId() == null) throw bad("Vui lòng chọn nhà cung cấp");
        AdminMasterData supplier = masterRepository.findById(request.nhaCungCapId())
                .filter(row -> SUPPLIER.equals(row.getKind())).orElseThrow(() -> bad("Nhà cung cấp không tồn tại"));
        ConsumableReceipt receipt = new ConsumableReceipt();
        receipt.setConsumable(item);
        receipt.setQuantity(request.soLuong());
        receipt.setUnitPrice(request.donGia());
        receipt.setSupplierId(supplier.getId());
        receipt.setSupplierName(supplier.getName());
        receipt.setReceivedOn(request.ngayNhap());
        receipt.setReceiptNumber(receiptNumber);
        receipt.setPurchaseReference(purchaseReference);
        receipt.setReceivedBy(auth.getName());
        item.setStockQuantity(item.getStockQuantity().add(request.soLuong()));
        item.setUnitPrice(request.donGia());
        consumableRepository.save(item);
        ConsumableReceipt saved = receiptRepository.save(receipt);
        ConsumableReceiptResponse response = consumableReceiptResponse(saved);
        activityLog.record(auth.getName(), "RECEIVE", "ConsumableReceipt", saved.getId(), null, response, null);
        return response;
    }

    @GetMapping("/vat-tu/lich-su")
    @Transactional(readOnly = true)
    public List<ConsumableMovementResponse> consumableMovements(
            @RequestParam(required = false) Integer vatTuId,
            @RequestParam(required = false) String phongBan,
            @RequestParam(required = false) LocalDate tuNgay,
            @RequestParam(required = false) LocalDate denNgay) {
        if (tuNgay != null && denNgay != null && tuNgay.isAfter(denNgay)) throw bad("Ngày bắt đầu không được sau ngày kết thúc");
        List<ConsumableMovementResponse> movements = new ArrayList<>();
        receiptRepository.findAllByOrderByReceivedOnDescIdDesc().stream()
                .map(this::receiptMovement).forEach(movements::add);
        issueRepository.findAll(Sort.by(Sort.Direction.DESC, "issuedAt")).stream()
                .map(this::issueMovement).forEach(movements::add);
        return movements.stream()
                .filter(row -> vatTuId == null || vatTuId.equals(row.vatTuId()))
                .filter(row -> phongBan == null || phongBan.isBlank()
                        || row.phongBan() != null && row.phongBan().toLowerCase(Locale.ROOT).contains(phongBan.trim().toLowerCase(Locale.ROOT)))
                .filter(row -> tuNgay == null || !row.ngay().isBefore(tuNgay))
                .filter(row -> denNgay == null || !row.ngay().isAfter(denNgay))
                .sorted(Comparator.comparing(ConsumableMovementResponse::ngay).reversed()
                        .thenComparing(ConsumableMovementResponse::id, Comparator.reverseOrder()))
                .toList();
    }

    @GetMapping("/vat-tu/bao-cao")
    @Transactional(readOnly = true)
    public List<ConsumableUsageResponse> consumableUsage(
            @RequestParam(required = false) LocalDate tuNgay,
            @RequestParam(required = false) LocalDate denNgay) {
        if (tuNgay != null && denNgay != null && tuNgay.isAfter(denNgay)) throw bad("Ngày bắt đầu không được sau ngày kết thúc");
        Map<String, BigDecimal> totals = new TreeMap<>();
        Map<String, ConsumableUsageResponse> rows = new HashMap<>();
        for (ConsumableIssue issue : issueRepository.findAll()) {
            LocalDate date = issue.getIssueDate();
            if (tuNgay != null && date.isBefore(tuNgay) || denNgay != null && date.isAfter(denNgay)) continue;
            Consumable item = issue.getConsumable();
            String department = issue.getDepartmentName() == null ? departmentName(issue.getDepartmentId()) : issue.getDepartmentName();
            String key = date.withDayOfMonth(1).toString() + "|" + Objects.toString(department, "Chưa phân phòng ban")
                    + "|" + item.getId();
            totals.merge(key, issue.getQuantity(), BigDecimal::add);
            rows.putIfAbsent(key, new ConsumableUsageResponse(date.toString().substring(0, 7),
                    Objects.toString(department, "Chưa phân phòng ban"), item.getId(), item.getCode(),
                    item.getName(), item.getUnit(), BigDecimal.ZERO));
        }
        return totals.entrySet().stream().map(entry -> {
            ConsumableUsageResponse base = rows.get(entry.getKey());
            return new ConsumableUsageResponse(base.thang(), base.phongBan(), base.vatTuId(),
                    base.maVatTu(), base.tenVatTu(), base.donVi(), entry.getValue());
        }).sorted(Comparator.comparing(ConsumableUsageResponse::thang).reversed()
                .thenComparing(ConsumableUsageResponse::phongBan)
                .thenComparing(ConsumableUsageResponse::tenVatTu)).toList();
    }

    @GetMapping("/vat-tu/yeu-cau")
    @Transactional(readOnly = true)
    public List<ConsumableRequestResponse> consumableRequests() {
        return consumableRequestRepository.findTop100ByOrderByCreatedAtDesc().stream().map(this::consumableRequestResponse).toList();
    }

    @PostMapping("/vat-tu/yeu-cau/{id}/tu-choi")
    @Transactional
    public ConsumableRequestResponse rejectConsumableRequest(@PathVariable Integer id,
            @RequestBody(required = false) ConsumableRequestReview request, Authentication auth) {
        com.example.quanlytaisan.entity.ConsumableRequest row = consumableRequestRepository.findByIdForUpdate(id)
                .orElseThrow(() -> notFound("Không tìm thấy yêu cầu vật tư"));
        if (!"PENDING".equals(row.getStatus())) throw conflict("Yêu cầu này đã được xử lý");
        String note = request == null ? null : trim(request.ghiChu());
        if (blank(note) || note.length() > 500) throw bad("Lý do từ chối là bắt buộc và tối đa 500 ký tự");
        row.setStatus("REJECTED");
        row.setReviewer(auth.getName());
        row.setReviewNote(note);
        row.setReviewedAt(Instant.now());
        ConsumableRequestResponse response = consumableRequestResponse(consumableRequestRepository.save(row));
        activityLog.record(auth.getName(), "REJECT", "ConsumableRequest", id, null, response, null);
        return response;
    }

    @PostMapping("/vat-tu/yeu-cau/{id}/xuat")
    @Transactional
    public ConsumableRequestResponse fulfillConsumableRequest(@PathVariable Integer id, Authentication auth) {
        com.example.quanlytaisan.entity.ConsumableRequest request = consumableRequestRepository.findByIdForUpdate(id)
                .orElseThrow(() -> notFound("Không tìm thấy yêu cầu vật tư"));
        if (!"PENDING".equals(request.getStatus())) throw conflict("Yêu cầu này đã được xử lý");
        Consumable item = consumableRepository.findByIdForUpdate(request.getConsumable().getId())
                .orElseThrow(() -> notFound("Không tìm thấy vật tư"));
        if (!item.isActive()) throw conflict("Vật tư đã ngừng sử dụng");
        if (item.getStockQuantity().compareTo(request.getQuantity()) < 0) throw conflict("Tồn kho không đủ để duyệt yêu cầu");
        ConsumableIssue issue = new ConsumableIssue();
        issue.setConsumable(item);
        issue.setQuantity(request.getQuantity());
        issue.setDepartmentId(request.getDepartmentId());
        issue.setDepartmentName(request.getDepartmentName());
        issue.setRecipient(request.getRequester());
        issue.setNotes("Yêu cầu vật tư #" + request.getId() + ": " + request.getReason());
        issue.setIssuedBy(auth.getName());
        issue.setIssueDate(LocalDate.now());
        item.setStockQuantity(item.getStockQuantity().subtract(request.getQuantity()));
        consumableRepository.save(item);
        issueRepository.save(issue);
        request.setStatus("ISSUED");
        request.setReviewer(auth.getName());
        request.setReviewedAt(Instant.now());
        ConsumableRequestResponse response = consumableRequestResponse(consumableRequestRepository.save(request));
        activityLog.record(auth.getName(), "ISSUE", "ConsumableRequest", id, null, response, null);
        return response;
    }

    @GetMapping("/hoa-don")
    @Transactional(readOnly = true)
    public List<InvoiceResponse> invoices() {
        return invoiceRepository.findAll(Sort.by(Sort.Direction.DESC, "invoiceDate")).stream().map(this::invoiceResponse).toList();
    }

    @GetMapping("/hoa-don/{id}")
    @Transactional(readOnly = true)
    public InvoiceResponse invoice(@PathVariable Integer id) {
        return invoiceResponse(invoiceRepository.findById(id).orElseThrow(() -> notFound("Không tìm thấy hóa đơn")));
    }

    @PostMapping("/hoa-don")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public InvoiceResponse createInvoice(@RequestBody InvoiceRequest request, Authentication auth) {
        if (request == null || blank(request.soHoaDon()) || request.soHoaDon().trim().length() > 50
                || request.ngayMua() == null) {
            throw bad("Số hóa đơn và ngày lập là bắt buộc");
        }
        if (request.ngayMua().isAfter(LocalDate.now())) throw bad("Ngày lập hóa đơn không được ở tương lai");
        validateInvoiceNumber(request.soHoaDon());
        validateSupplier(request.nhaCungCapId());
        validateInvoiceDescription(request.moTa());
        if (request.nhaCungCap() != null && request.nhaCungCap().trim().length() > 150) {
            throw bad("Tên nhà cung cấp tối đa 150 ký tự");
        }
        AdminInvoice row = new AdminInvoice();
        row.setInvoiceNumber(request.soHoaDon().trim());
        row.setInvoiceDate(request.ngayMua());
        row.setSupplierId(request.nhaCungCapId());
        row.setSupplierName(request.nhaCungCapId() == null ? trim(request.nhaCungCap()) : null);
        row.setDescription(trim(request.moTa()));
        row.setPurchaseAssetId(request.taiSanId());

        BigDecimal total = BigDecimal.ZERO;
        if (request.chiTiet() != null && !request.chiTiet().isEmpty()) {
            for (InvoiceLineRequest line : request.chiTiet()) {
                total = total.add(validateAndAddInvoiceLine(row, line, request.ngayMua(), request.nhaCungCapId()));
            }
        } else if (request.taiSanId() != null) {
            TaiSan asset = assetRepository.findById(request.taiSanId())
                    .orElseThrow(() -> bad("Tài sản liên kết không tồn tại"));
            if (request.tongTien() == null || request.tongTien().compareTo(BigDecimal.ZERO) <= 0) {
                throw bad("Tổng tiền phải lớn hơn 0");
            }
            InvoiceLineRequest legacyLine = new InvoiceLineRequest("TAI_SAN", asset.getTenTaiSan(),
                    BigDecimal.ONE, request.tongTien(), asset.getTaiSanId(), null);
            total = validateAndAddInvoiceLine(row, legacyLine, request.ngayMua(), request.nhaCungCapId());
        } else if (request.tongTien() != null && request.tongTien().compareTo(BigDecimal.ZERO) > 0) {
            total = request.tongTien();
        } else {
            throw bad("Hóa đơn phải có ít nhất một dòng hàng hoặc tổng tiền lớn hơn 0");
        }
        row.setTotalAmount(validateInvoiceTotal(total));
        row.setPaymentStatus("CHUA_TRA");
        InvoiceResponse response = invoiceResponse(invoiceRepository.save(row));
        activityLog.record(auth.getName(), "CREATE", "Invoice", response.id(), null, response, null);
        return response;
    }

    @PostMapping("/hoa-don/tu-don-mua/{orderId}")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public InvoiceResponse createInvoiceFromPurchaseOrder(@PathVariable Integer orderId,
            @RequestBody OrderInvoiceRequest request, Authentication auth) {
        if (request == null || blank(request.soHoaDon()) || request.ngayLap() == null) {
            throw bad("Số hóa đơn và ngày lập là bắt buộc");
        }
        if (request.ngayLap().isAfter(LocalDate.now())) throw bad("Ngày lập hóa đơn không được ở tương lai");
        validateInvoiceNumber(request.soHoaDon());
        validateInvoiceDescription(request.moTa());
        ThuongMaiOrder order = commerceOrderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> notFound("Không tìm thấy đơn mua"));
        if (!"MUA".equalsIgnoreCase(order.getLoaiDon())
                || !Set.of("DA_NHAN", "NHAN_MOT_PHAN").contains(order.getTrangThai())) {
            throw conflict("Chỉ tạo hóa đơn từ đơn mua đã nhận toàn bộ hoặc một phần hàng");
        }
        AdminInvoice row = new AdminInvoice();
        row.setInvoiceNumber(request.soHoaDon().trim());
        row.setInvoiceDate(request.ngayLap());
        row.setSupplierId(order.getSupplierId());
        row.setSupplierName(order.getSupplierId() == null ? order.getSupplierName() : null);
        row.setPurchaseOrderId(orderId);
        row.setDescription(trim(request.moTa()));
        BigDecimal total = BigDecimal.ZERO;
        Map<Integer, BigDecimal> invoicedQuantityByItem = new HashMap<>();
        for (AdminInvoice previousInvoice : invoiceRepository.findByPurchaseOrderId(orderId)) {
            for (AdminInvoiceLine previousLine : invoiceLineRepository
                    .findByInvoice_IdOrderByIdAsc(previousInvoice.getId())) {
                if (previousLine.getPurchaseOrderItemId() != null) {
                    invoicedQuantityByItem.merge(previousLine.getPurchaseOrderItemId(),
                            previousLine.getQuantity(), BigDecimal::add);
                }
            }
        }
        for (ThuongMaiOrderItem orderItem : order.getChiTiet()) {
            BigDecimal remaining = BigDecimal.valueOf(orderItem.getSoLuongDaNhan())
                    .subtract(invoicedQuantityByItem.getOrDefault(orderItem.getId(), BigDecimal.ZERO));
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) continue;
            InvoiceLineRequest line = new InvoiceLineRequest(orderItem.getLoaiDong(), orderItem.getTenSanPham(),
                    remaining, orderItem.getDonGia(), orderItem.getTaiSanId(),
                    orderItem.getConsumableId());
            total = total.add(validateAndAddInvoiceLine(row, line, request.ngayLap(), order.getSupplierId(),
                    orderItem.getId()));
        }
        if (total.compareTo(BigDecimal.ZERO) <= 0) throw conflict("Đơn mua chưa có số lượng hàng đã nhận để lập hóa đơn");
        row.setTotalAmount(validateInvoiceTotal(total));
        row.setPaymentStatus("CHUA_TRA");
        InvoiceResponse response = invoiceResponse(invoiceRepository.save(row));
        activityLog.record(auth.getName(), "CREATE_FROM_PURCHASE_ORDER", "Invoice", response.id(), null,
                response, "Lập hóa đơn từ đơn mua " + order.getMaDonHang());
        return response;
    }

    @PostMapping("/hoa-don/{id}/thanh-toan")
    @Transactional
    public InvoiceResponse recordInvoicePayment(@PathVariable Integer id, @RequestBody InvoicePaymentRequest request,
            Authentication auth) {
        AdminInvoice row = invoiceRepository.findByIdForUpdate(id)
                .orElseThrow(() -> notFound("Không tìm thấy hóa đơn"));
        if (request == null || request.ngayThanhToan() == null || request.soTien() == null
                || request.soTien().compareTo(BigDecimal.ZERO) <= 0 || blank(request.phuongThuc())
                || request.soTien().scale() > 2 || request.phuongThuc().trim().length() > 40) {
            throw bad("Ngày, số tiền thanh toán dương và phương thức thanh toán là bắt buộc");
        }
        if (request.ngayThanhToan().isAfter(LocalDate.now())) throw bad("Ngày thanh toán không được ở tương lai");
        BigDecimal balance = row.getTotalAmount().subtract(totalPaid(row));
        if (request.soTien().compareTo(balance) > 0) throw conflict("Số tiền thanh toán vượt quá số còn nợ");
        AdminInvoicePayment payment = new AdminInvoicePayment();
        payment.setInvoice(row);
        payment.setPaymentDate(request.ngayThanhToan());
        payment.setAmount(request.soTien().setScale(2, RoundingMode.HALF_UP));
        payment.setMethod(request.phuongThuc().trim());
        payment.setRecordedBy(auth.getName());
        invoicePaymentRepository.save(payment);
        refreshPaymentStatus(row);
        InvoiceResponse response = invoiceResponse(invoiceRepository.save(row));
        activityLog.record(auth.getName(), "PAYMENT", "Invoice", id, null, response, null);
        return response;
    }

    @PostMapping("/hoa-don/{id}/dieu-chinh")
    @Transactional
    public InvoiceResponse adjustInvoice(@PathVariable Integer id, @RequestBody InvoiceAdjustmentRequest request,
            Authentication auth) {
        AdminInvoice row = invoiceRepository.findByIdForUpdate(id)
                .orElseThrow(() -> notFound("Không tìm thấy hóa đơn"));
        if (request == null || request.ngayDieuChinh() == null || request.thayDoi() == null
                || request.thayDoi().compareTo(BigDecimal.ZERO) == 0 || blank(request.lyDo())
                || request.thayDoi().scale() > 2 || request.lyDo().trim().length() > 500) {
            throw bad("Ngày, số tiền điều chỉnh khác 0 và lý do là bắt buộc");
        }
        if (request.ngayDieuChinh().isAfter(LocalDate.now())) throw bad("Ngày điều chỉnh không được ở tương lai");
        BigDecimal adjustedTotal = row.getTotalAmount().add(request.thayDoi()).setScale(2, RoundingMode.HALF_UP);
        if (adjustedTotal.compareTo(BigDecimal.ZERO) <= 0 || adjustedTotal.compareTo(totalPaid(row)) < 0) {
            throw conflict("Tổng hóa đơn sau điều chỉnh phải dương và không thấp hơn số đã thanh toán");
        }
        AdminInvoiceAdjustment adjustment = new AdminInvoiceAdjustment();
        adjustment.setInvoice(row);
        adjustment.setAdjustmentDate(request.ngayDieuChinh());
        adjustment.setAmountDelta(request.thayDoi().setScale(2, RoundingMode.HALF_UP));
        adjustment.setReason(request.lyDo().trim());
        adjustment.setRecordedBy(auth.getName());
        invoiceAdjustmentRepository.save(adjustment);
        row.setTotalAmount(adjustedTotal);
        refreshPaymentStatus(row);
        InvoiceResponse response = invoiceResponse(invoiceRepository.save(row));
        activityLog.record(auth.getName(), "ADJUST", "Invoice", id, null, response, request.lyDo().trim());
        return response;
    }

    @PostMapping(value = "/hoa-don/{id}/tep-dinh-kem", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public InvoiceResponse attachInvoiceFile(@PathVariable Integer id, @RequestPart("file") MultipartFile file,
            Authentication auth) {
        AdminInvoice row = invoiceRepository.findByIdForUpdate(id)
                .orElseThrow(() -> notFound("Không tìm thấy hóa đơn"));
        if (invoiceAttachmentRepository.existsByInvoice_Id(id)) {
            throw conflict("Hóa đơn đã có tệp đính kèm; không thể ghi đè chứng từ");
        }
        String fileName = file.getOriginalFilename();
        String contentType = file.getContentType();
        try {
            byte[] data = file.getBytes();
            if (!validInvoiceAttachment(fileName, contentType, data)) {
                throw bad("Chỉ chấp nhận PDF, PNG hoặc JPEG hợp lệ, dung lượng tối đa 10 MB");
            }
            String safeName = fileName.replace('\\', '/');
            safeName = safeName.substring(safeName.lastIndexOf('/') + 1);
            safeName = safeName.substring(0, Math.min(255, safeName.length()));
            row.setAttachmentName(safeName);
            row.setAttachmentContentType(contentType);
            AdminInvoiceAttachment attachment = new AdminInvoiceAttachment();
            attachment.setInvoice(row);
            attachment.setFileName(safeName);
            attachment.setContentType(contentType);
            attachment.setData(data);
            invoiceAttachmentRepository.save(attachment);
        } catch (java.io.IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không đọc được tệp hóa đơn", exception);
        }
        InvoiceResponse response = invoiceResponse(invoiceRepository.save(row));
        activityLog.record(auth.getName(), "ATTACH", "Invoice", id, null, response, null);
        return response;
    }

    @GetMapping("/hoa-don/{id}/tep-dinh-kem")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> downloadInvoiceFile(@PathVariable Integer id) {
        invoiceRepository.findById(id)
                .orElseThrow(() -> notFound("Không tìm thấy hóa đơn"));
        AdminInvoiceAttachment attachment = invoiceAttachmentRepository.findByInvoice_Id(id)
                .orElseThrow(() -> notFound("Hóa đơn chưa có tệp đính kèm"));
        MediaType contentType = MediaType.parseMediaType(attachment.getContentType());
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(attachment.getFileName(), java.nio.charset.StandardCharsets.UTF_8).build();
        return ResponseEntity.ok().contentType(contentType).header(HttpHeaders.CONTENT_DISPOSITION,
                disposition.toString()).body(attachment.getData());
    }

    @GetMapping("/cong-no-nha-cung-cap")
    @Transactional(readOnly = true)
    public List<SupplierDebtResponse> supplierDebts() {
        Map<String, BigDecimal[]> totals = new LinkedHashMap<>();
        Map<String, Integer> supplierIds = new HashMap<>();
        Map<String, String> supplierNames = new HashMap<>();
        for (AdminInvoice invoice : invoiceRepository.findAll()) {
            String name = invoice.getSupplierId() == null ? invoice.getSupplierName()
                    : masterRepository.findById(invoice.getSupplierId()).map(AdminMasterData::getName)
                            .orElse(invoice.getSupplierName());
            String key = invoice.getSupplierId() == null ? "name:" + Objects.toString(name, "Không xác định")
                    : "id:" + invoice.getSupplierId();
            BigDecimal[] values = totals.computeIfAbsent(key, ignored -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            values[0] = values[0].add(invoice.getTotalAmount());
            values[1] = values[1].add(totalPaid(invoice));
            supplierIds.putIfAbsent(key, invoice.getSupplierId());
            supplierNames.putIfAbsent(key, name == null ? "Không xác định" : name);
        }
        return totals.entrySet().stream().map(entry -> new SupplierDebtResponse(supplierIds.get(entry.getKey()),
                supplierNames.get(entry.getKey()), entry.getValue()[0], entry.getValue()[1],
                entry.getValue()[0].subtract(entry.getValue()[1]).max(BigDecimal.ZERO))).toList();
    }

    @GetMapping("/khau-hao")
    @Transactional(readOnly = true)
    public List<DepreciationRow> depreciation(@RequestParam(required = false) Integer taiSanId) {
        List<TaiSan> assets = taiSanId == null ? assetRepository.findAll() : List.of(
                assetRepository.findById(taiSanId).orElseThrow(() -> notFound("Không tìm thấy tài sản")));
        Map<Integer, LocalDate> disposalDates = new HashMap<>();
        for (ThuongMaiOrder order : commerceOrderRepository.findAll()) {
            if (!"BAN".equals(order.getLoaiDon()) || !"DA_THANH_LY".equals(order.getTrangThai())
                    || order.getNgayThanhLy() == null) continue;
            for (ThuongMaiOrderItem item : order.getChiTiet()) {
                if (item.getTaiSanId() != null) disposalDates.put(item.getTaiSanId(), order.getNgayThanhLy());
            }
        }
        List<DepreciationRow> result = new ArrayList<>();
        for (TaiSan asset : assets) {
            BigDecimal cost = asset.getGiaMua();
            LocalDate bought = asset.getNgayMua();
            Integer life = effectiveUsefulLife(asset);
            if (cost == null || bought == null || life == null || life <= 0) continue;
            BigDecimal annual = cost.divide(BigDecimal.valueOf(life), 2, RoundingMode.HALF_UP);
            LocalDate disposalDate = disposalDates.get(asset.getTaiSanId());
            boolean retired = isDisposed(asset.getTinhTrang());
            if (retired && disposalDate == null) disposalDate = LocalDate.now();
            int yearsToDispose = disposalDate == null ? life
                    : Math.min(life, disposalDate.getYear() - bought.getYear() + 1);
            for (int year = 1; year <= yearsToDispose; year++) {
                BigDecimal start = cost.subtract(annual.multiply(BigDecimal.valueOf(year - 1))).max(BigDecimal.ZERO);
                BigDecimal depreciation = year == life ? start : annual;
                int calendarYear = bought.getYear() + year - 1;
                if (disposalDate != null && calendarYear == disposalDate.getYear()) {
                    LocalDate yearStart = LocalDate.of(calendarYear, 1, 1);
                    long daysHeld = java.time.temporal.ChronoUnit.DAYS.between(yearStart, disposalDate.plusDays(1));
                    long daysInYear = yearStart.isLeapYear() ? 366 : 365;
                    depreciation = depreciation.multiply(BigDecimal.valueOf(Math.max(0, Math.min(daysHeld, daysInYear))))
                            .divide(BigDecimal.valueOf(daysInYear), 2, RoundingMode.HALF_UP);
                }
                depreciation = depreciation.min(start);
                BigDecimal end = start.subtract(depreciation).max(BigDecimal.ZERO);
                result.add(new DepreciationRow(asset.getTaiSanId(), asset.getMaTaiSan(), asset.getTenTaiSan(),
                        calendarYear, depreciation, start, end));
            }
        }
        return result;
    }

    @PutMapping("/khau-hao/{taiSanId}/thoi-gian")
    @Transactional
    public List<DepreciationRow> updateAssetUsefulLife(@PathVariable Integer taiSanId,
            @RequestBody DepreciationLifeRequest request, Authentication auth) {
        TaiSan asset = assetRepository.findById(taiSanId)
                .orElseThrow(() -> notFound("Không tìm thấy tài sản"));
        if (request == null || request.thoiGianKhauHaoNam() == null
                || request.thoiGianKhauHaoNam() < 1 || request.thoiGianKhauHaoNam() > 100) {
            throw bad("Thời gian khấu hao riêng phải là số nguyên dương, tối đa 100 năm");
        }
        if (isDisposed(asset.getTinhTrang())) throw conflict("Tài sản đã thanh lý không thể thay đổi khấu hao");
        Integer previous = effectiveUsefulLife(asset);
        asset.setThoiGianKhauHaoNam(request.thoiGianKhauHaoNam());
        assetRepository.save(asset);
        activityLog.record(auth.getName(), "UPDATE_DEPRECIATION_LIFE", "TaiSan", taiSanId,
                previous, request.thoiGianKhauHaoNam(), "Đã cập nhật thời gian khấu hao riêng.");
        return depreciation(taiSanId);
    }

    @GetMapping("/reports")
    @Transactional(readOnly = true)
    public List<Metric> reports(@RequestParam(defaultValue = "phongBan") String groupBy) {
        Dashboard data = dashboard(null, null, null, null, null);
        return switch (groupBy.trim().toLowerCase(Locale.ROOT)) {
            case "phongban", "phong_ban", "theophongban", "department" -> data.theoPhongBan();
            case "loai", "loaitaisan", "loai_tai_san", "theoloai", "type", "category" -> data.theoLoai();
            case "trangthai", "trang_thai", "theotrangthai", "status" -> data.theoTrangThai();
            default -> throw bad("groupBy phải là phongBan, loai hoặc trangThai");
        };
    }

    @GetMapping("/kiem-ke")
    @Transactional(readOnly = true)
    public List<StocktakeResponse> stocktakes() {
        return stocktakeRepository.findAll(Sort.by(Sort.Direction.DESC, "stocktakeDate")).stream().map(this::stocktakeResponse).toList();
    }

    @GetMapping("/kiem-ke/my")
    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public List<StocktakeResponse> myStocktakes(Authentication auth) {
        return stocktakeRepository.findByResponsibleUsernameIgnoreCaseOrderByStocktakeDateDesc(auth.getName())
                .stream().map(this::stocktakeResponse).toList();
    }

    @GetMapping("/kiem-ke/{id}")
    @PreAuthorize("hasRole('ADMIN') or @stocktakeAuthorization.canAccess(#p0, authentication)")
    @Transactional(readOnly = true)
    public StocktakeResponse stocktake(@PathVariable Integer id) {
        return stocktakeResponse(stocktakeRepository.findById(id).orElseThrow(() -> notFound("Không tìm thấy đợt kiểm kê")));
    }

    @PostMapping("/kiem-ke")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public StocktakeResponse createStocktake(@RequestBody StocktakeRequest request, Authentication auth) {
        validateStocktake(request);
        if (request.trangThai() != null && !"DRAFT".equalsIgnoreCase(request.trangThai())) {
            throw bad("Đợt kiểm kê mới phải bắt đầu ở trạng thái Nháp");
        }
        StocktakeSession row = new StocktakeSession();
        applyStocktake(row, request);
        String responsible = blank(request.nguoiPhuTrach()) ? auth.getName() : request.nguoiPhuTrach().trim();
        AppUser responsibleUser = userRepository.findByUsernameIgnoreCase(responsible)
                .filter(AppUser::isEnabled).orElseThrow(() -> bad("Tài khoản người phụ trách không tồn tại hoặc đã bị khóa"));
        row.setResponsibleUsername(responsibleUser.getUsername());
        row.setCheckerName(blank(request.nguoiKiem()) ? responsibleUser.getFullName() : request.nguoiKiem().trim());
        if (blank(row.getCheckerName())) row.setCheckerName(responsibleUser.getUsername());
        row.setConfirmerName(trim(request.nguoiXacNhan()));
        row.setStatus("DRAFT");
        StocktakeResponse response = stocktakeResponse(stocktakeRepository.save(row));
        activityLog.record(auth.getName(), "CREATE", "StocktakeSession", response.id(), null, response, null);
        return response;
    }

    @PutMapping("/kiem-ke/{id}")
    @Transactional
    public StocktakeResponse updateStocktake(@PathVariable Integer id, @RequestBody StocktakeRequest request, Authentication auth) {
        StocktakeSession row = stocktakeRepository.findByIdForUpdate(id).orElseThrow(() -> notFound("Không tìm thấy đợt kiểm kê"));
        if (!"DRAFT".equals(row.getStatus())) throw conflict("Chỉ được chỉnh sửa thông tin khi đợt đang ở trạng thái Nháp");
        StocktakeResponse old = stocktakeResponse(row);
        validateStocktake(request);
        if (request.trangThai() != null && !"DRAFT".equalsIgnoreCase(request.trangThai())) {
            throw bad("Hãy dùng thao tác bắt đầu, hoàn thành hoặc hủy để chuyển trạng thái");
        }
        applyStocktake(row, request);
        if (!blank(request.nguoiPhuTrach())) {
            AppUser responsible = userRepository.findByUsernameIgnoreCase(request.nguoiPhuTrach().trim())
                    .filter(AppUser::isEnabled).orElseThrow(() -> bad("Tài khoản người phụ trách không tồn tại hoặc đã bị khóa"));
            row.setResponsibleUsername(responsible.getUsername());
        }
        row.setCheckerName(trim(request.nguoiKiem()));
        row.setConfirmerName(trim(request.nguoiXacNhan()));
        StocktakeResponse response = stocktakeResponse(stocktakeRepository.save(row));
        activityLog.record(auth.getName(), "UPDATE", "StocktakeSession", id, old, response, null);
        return response;
    }

    @PostMapping("/kiem-ke/{id}/start")
    @PreAuthorize("hasRole('ADMIN') or @stocktakeAuthorization.canAccess(#p0, authentication)")
    @Transactional
    public StocktakeResponse startStocktake(@PathVariable Integer id, Authentication auth) {
        StocktakeSession session = stocktakeRepository.findByIdForUpdate(id).orElseThrow(() -> notFound("Không tìm thấy đợt kiểm kê"));
        if (!"DRAFT".equals(session.getStatus())) throw conflict("Chỉ có thể bắt đầu đợt kiểm kê đang ở trạng thái Nháp");
        List<TaiSan> assets = assetRepository.findAll(Sort.by("taiSanId"));
        Map<Integer, TaiSanHolderInfo> holders = new HashMap<>();
        if (!assets.isEmpty()) {
            for (TaiSanHolderInfo holder : assignmentRepository.findCurrentHolders(
                    assets.stream().map(TaiSan::getTaiSanId).toList())) {
                holders.putIfAbsent(holder.getTaiSanId(), holder);
            }
        }
        if ("DEPARTMENT".equals(session.getScopeType())) {
            assets = assets.stream().filter(asset -> {
                TaiSanHolderInfo holder = holders.get(asset.getTaiSanId());
                return holder != null && session.getScopeValue().equalsIgnoreCase(trim(holder.getPhongBan()));
            }).toList();
        } else if ("LOCATION".equals(session.getScopeType())) {
            assets = assets.stream().filter(asset -> session.getScopeValue() != null
                    && session.getScopeValue().equalsIgnoreCase(assetLocation(asset))).toList();
        }
        if (assets.isEmpty()) throw conflict("Phạm vi này không có tài sản để kiểm kê");
        List<Integer> assetIds = assets.stream().map(TaiSan::getTaiSanId).toList();
        List<TaiSan> lockedAssets = assetRepository.findAllByIdForUpdate(assetIds);
        List<Integer> conflictingIds = stocktakeItemRepository.findAssetIdsInOpenSessions(assetIds);
        if (!conflictingIds.isEmpty()) {
            Set<Integer> conflictSet = new HashSet<>(conflictingIds);
            String codes = lockedAssets.stream().filter(asset -> conflictSet.contains(asset.getTaiSanId()))
                    .map(TaiSan::getMaTaiSan).limit(5).collect(Collectors.joining(", "));
            throw conflict("Một số tài sản đang thuộc đợt kiểm kê mở khác: " + codes);
        }
        for (TaiSan asset : lockedAssets) {
            TaiSanHolderInfo holder = holders.get(asset.getTaiSanId());
            StocktakeItem item = new StocktakeItem();
            item.setSession(session);
            item.setAsset(asset);
            item.setConditionStatus("CO_MAT");
            item.setBookLocation(assetLocation(asset));
            item.setBookHolder(holder == null ? null : holder.getNguoiNhan());
            stocktakeItemRepository.save(item);
        }
        session.setStatus("OPEN");
        StocktakeResponse response = stocktakeResponse(stocktakeRepository.save(session));
        activityLog.record(auth.getName(), "UPDATE", "StocktakeSession", id, "DRAFT", response, "Đã tạo snapshot tài sản theo sổ.");
        return response;
    }

    @PostMapping("/kiem-ke/{id}/complete")
    @PreAuthorize("hasRole('ADMIN') or @stocktakeAuthorization.canAccess(#p0, authentication)")
    @Transactional
    public StocktakeResponse completeStocktake(@PathVariable Integer id, Authentication auth) {
        StocktakeSession session = stocktakeRepository.findByIdForUpdate(id).orElseThrow(() -> notFound("Không tìm thấy đợt kiểm kê"));
        if (!"OPEN".equals(session.getStatus())) throw conflict("Chỉ có thể hoàn thành đợt đang kiểm");
        List<StocktakeItem> items = stocktakeItemRepository.findBySession_IdOrderByIdAsc(id);
        long unchecked = items.stream().filter(item -> item.getCheckedAt() == null).count();
        if (unchecked > 0) throw conflict("Còn " + unchecked + " tài sản chưa kiểm; không thể hoàn thành đợt");
        if (blank(session.getCheckerName()) || blank(session.getConfirmerName())) {
            throw conflict("Cần khai báo người kiểm và người xác nhận trước khi hoàn thành");
        }
        for (StocktakeItem item : items) {
            if ("THIEU".equals(item.getConditionStatus())) item.getAsset().setTinhTrang("Thiếu");
            if ("HONG".equals(item.getConditionStatus())) item.getAsset().setTinhTrang("Bảo trì");
        }
        assetRepository.saveAll(items.stream().map(StocktakeItem::getAsset).toList());
        session.setStatus("COMPLETED");
        session.setEndDate(LocalDate.now());
        session.setReportPdf(stocktakePdfGenerator.generate(session, items,
                stocktakeExtraRepository.findBySession_IdOrderByIdAsc(id)));
        StocktakeResponse response = stocktakeResponse(stocktakeRepository.save(session));
        activityLog.record(auth.getName(), "UPDATE", "StocktakeSession", id, "OPEN", response, "Đã khóa đợt và lưu biên bản kiểm kê.");
        return response;
    }

    @PostMapping("/kiem-ke/{id}/cancel")
    @PreAuthorize("hasRole('ADMIN') or @stocktakeAuthorization.canAccess(#p0, authentication)")
    @Transactional
    public StocktakeResponse cancelStocktake(@PathVariable Integer id, Authentication auth) {
        StocktakeSession session = stocktakeRepository.findByIdForUpdate(id).orElseThrow(() -> notFound("Không tìm thấy đợt kiểm kê"));
        if ("COMPLETED".equals(session.getStatus())) throw conflict("Đợt kiểm kê đã hoàn thành và không thể hủy");
        if ("CANCELLED".equals(session.getStatus())) return stocktakeResponse(session);
        StocktakeResponse old = stocktakeResponse(session);
        session.setStatus("CANCELLED");
        StocktakeResponse response = stocktakeResponse(stocktakeRepository.save(session));
        activityLog.record(auth.getName(), "UPDATE", "StocktakeSession", id, old, response, "Đợt kiểm kê đã bị hủy.");
        return response;
    }

    @DeleteMapping("/kiem-ke/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void deleteStocktake(@PathVariable Integer id, Authentication auth) {
        StocktakeSession row = stocktakeRepository.findByIdForUpdate(id).orElseThrow(() -> notFound("Không tìm thấy đợt kiểm kê"));
        if (!"DRAFT".equals(row.getStatus())) throw conflict("Chỉ có thể xóa đợt kiểm kê Nháp");
        if (stocktakeItemRepository.existsBySession_Id(id)) throw conflict("Đợt đã có dữ liệu kiểm kê và không thể xóa");
        StocktakeResponse old = stocktakeResponse(row);
        stocktakeRepository.delete(row);
        activityLog.record(auth.getName(), "DELETE", "StocktakeSession", id, old, null, null);
    }

    @GetMapping("/kiem-ke/{id}/items")
    @PreAuthorize("hasRole('ADMIN') or @stocktakeAuthorization.canAccess(#p0, authentication)")
    @Transactional(readOnly = true)
    public List<StocktakeItemResponse> stocktakeItems(@PathVariable Integer id) {
        stocktakeRepository.findById(id).orElseThrow(() -> notFound("Không tìm thấy đợt kiểm kê"));
        return stocktakeItemRepository.findBySession_IdOrderByIdAsc(id).stream().map(this::stocktakeItemResponse).toList();
    }

    @PostMapping("/kiem-ke/{id}/items")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public StocktakeItemResponse createStocktakeItem(@PathVariable Integer id, @RequestBody StocktakeItemRequest request, Authentication auth) {
        throw conflict("Danh sách theo sổ được chụp tự động khi bắt đầu đợt; hãy dùng chức năng ghi nhận tài sản thừa");
    }

    @PutMapping("/kiem-ke/{id}/items/{itemId}")
    @PreAuthorize("hasRole('ADMIN') or @stocktakeAuthorization.canAccess(#p0, authentication)")
    @Transactional
    public StocktakeItemResponse updateStocktakeItem(@PathVariable Integer id, @PathVariable Integer itemId,
            @RequestBody StocktakeItemRequest request, Authentication auth) {
        StocktakeSession session = stocktakeRepository.findByIdForUpdate(id).orElseThrow(() -> notFound("Không tìm thấy đợt kiểm kê"));
        if (!"OPEN".equals(session.getStatus())) throw conflict("Chỉ được ghi kết quả khi đợt đang kiểm");
        StocktakeItem item = stocktakeItemRepository.findById(itemId).orElseThrow(() -> notFound("Không tìm thấy dòng kiểm kê"));
        if (!item.getSession().getId().equals(id)) throw notFound("Dòng kiểm kê không thuộc đợt này");
        validateStocktakeItem(request, false);
        StocktakeItemResponse old = stocktakeItemResponse(item);
        String status = normalizeStocktakeStatus(request.tinhTrangThucTe());
        if ("SAI_VI_TRI".equals(status) && blank(request.viTriThucTe())) {
            throw bad("Vui lòng nhập vị trí thực tế khi chọn Sai vị trí");
        }
        item.setConditionStatus(status);
        item.setActualLocation(trim(request.viTriThucTe()));
        item.setNotes(trim(request.ghiChu()));
        item.setCheckedBy(auth.getName());
        item.setCheckedAt(Instant.now());
        StocktakeItemResponse response = stocktakeItemResponse(stocktakeItemRepository.save(item));
        activityLog.record(auth.getName(), "UPDATE", "StocktakeItem", itemId, old, response, null);
        return response;
    }

    @DeleteMapping("/kiem-ke/{id}/items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void deleteStocktakeItem(@PathVariable Integer id, @PathVariable Integer itemId, Authentication auth) {
        throw conflict("Không thể xóa tài sản khỏi snapshot kiểm kê");
    }

    @GetMapping("/kiem-ke/{id}/extras")
    @PreAuthorize("hasRole('ADMIN') or @stocktakeAuthorization.canAccess(#p0, authentication)")
    @Transactional(readOnly = true)
    public List<StocktakeExtraResponse> stocktakeExtras(@PathVariable Integer id) {
        stocktakeRepository.findById(id).orElseThrow(() -> notFound("Không tìm thấy đợt kiểm kê"));
        return stocktakeExtraRepository.findBySession_IdOrderByIdAsc(id).stream().map(this::stocktakeExtraResponse).toList();
    }

    @PostMapping("/kiem-ke/{id}/extras")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN') or @stocktakeAuthorization.canAccess(#p0, authentication)")
    @Transactional
    public StocktakeExtraResponse createStocktakeExtra(@PathVariable Integer id, @RequestBody StocktakeExtraRequest request,
            Authentication auth) {
        StocktakeSession session = stocktakeRepository.findByIdForUpdate(id).orElseThrow(() -> notFound("Không tìm thấy đợt kiểm kê"));
        if (!"OPEN".equals(session.getStatus())) throw conflict("Chỉ được ghi nhận tài sản thừa khi đợt đang kiểm");
        if (request == null || blank(request.maTaiSan()) || blank(request.tenTaiSan())) {
            throw bad("Mã và tên tài sản thừa là bắt buộc");
        }
        if (request.maTaiSan().trim().length() > 30 || request.tenTaiSan().trim().length() > 150
                || (request.viTriThucTe() != null && request.viTriThucTe().length() > 250)
                || (request.ghiChu() != null && request.ghiChu().length() > 500)) {
            throw bad("Thông tin tài sản thừa vượt quá độ dài cho phép");
        }
        if (stocktakeExtraRepository.existsBySession_IdAndAssetCodeIgnoreCase(id, request.maTaiSan().trim())) {
            throw conflict("Mã tài sản thừa đã được ghi nhận trong đợt này");
        }
        StocktakeExtra extra = new StocktakeExtra();
        extra.setSession(session);
        extra.setAssetCode(request.maTaiSan().trim());
        extra.setAssetName(request.tenTaiSan().trim());
        extra.setActualLocation(trim(request.viTriThucTe()));
        extra.setNotes(trim(request.ghiChu()));
        extra.setCheckedBy(auth.getName());
        StocktakeExtraResponse response = stocktakeExtraResponse(stocktakeExtraRepository.save(extra));
        activityLog.record(auth.getName(), "CREATE", "StocktakeExtra", response.id(), null, response, null);
        return response;
    }

    @PostMapping("/kiem-ke/{id}/items/{itemId}/maintenance")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN') or @stocktakeAuthorization.canAccess(#p0, authentication)")
    @Transactional
    public Map<String, Integer> createStocktakeMaintenance(@PathVariable Integer id, @PathVariable Integer itemId,
            Authentication auth) {
        StocktakeSession session = stocktakeRepository.findByIdForUpdate(id)
                .orElseThrow(() -> notFound("Không tìm thấy đợt kiểm kê"));
        if (!"OPEN".equals(session.getStatus())) throw conflict("Chỉ được tạo phiếu sửa chữa khi đợt kiểm kê đang mở");
        StocktakeItem item = stocktakeItemRepository.findById(itemId).orElseThrow(() -> notFound("Không tìm thấy dòng kiểm kê"));
        if (!item.getSession().getId().equals(id)) throw notFound("Dòng kiểm kê không thuộc đợt này");
        if (!"HONG".equals(item.getConditionStatus())) throw bad("Chỉ có thể tạo phiếu bảo trì cho tài sản được ghi nhận Hỏng");
        if (item.getMaintenanceRequestId() != null) {
            return Map.of("maintenanceRequestId", item.getMaintenanceRequestId());
        }
        TaiSan asset = item.getAsset();
        if (Set.of("Thanh lý", "Đã thanh lý", "Chờ thanh lý").contains(asset.getTinhTrang())) {
            throw conflict("Tài sản đã thanh lý hoặc đang chờ thanh lý không thể tạo phiếu sửa chữa");
        }
        if (maintenanceRepository.existsByTaiSan_TaiSanIdAndTrangThaiIn(item.getAsset().getTaiSanId(), List.of("YEU_CAU", "DANG_XU_LY"))) {
            throw conflict("Tài sản đã có yêu cầu bảo trì đang mở");
        }
        String previousStatus = asset.getTinhTrang();
        asset.setTinhTrang("Bảo trì");
        assetRepository.save(asset);
        YeuCauBaoTri maintenance = new YeuCauBaoTri();
        maintenance.setTaiSan(asset);
        maintenance.setTieuDe("Bảo trì từ đợt kiểm kê: " + session.getName());
        maintenance.setMoTa(blank(item.getNotes()) ? "Tài sản được ghi nhận hỏng trong đợt kiểm kê." : item.getNotes());
        maintenance.setNguoiYeuCau(auth.getName());
        maintenance.setTrangThai("YEU_CAU");
        maintenance.setTrangThaiTruocBaoTri(previousStatus);
        maintenance.setNgayBaoHong(LocalDate.now());
        maintenance.setMucDo("NGHIEM_TRONG");
        maintenance.setNoiXuLy("NOI_BO");
        maintenance.setLoaiYeuCau("SUA_CHUA");
        maintenance.setNguonTao("KIEM_KE");
        maintenance.setPhongBan(assignmentRepository.findCurrentHolders(List.of(asset.getTaiSanId())).stream()
                .findFirst().map(TaiSanHolderInfo::getPhongBan).orElse(null));
        YeuCauBaoTri savedMaintenance = maintenanceRepository.save(maintenance);
        item.setMaintenanceRequestId(savedMaintenance.getId());
        stocktakeItemRepository.save(item);
        activityLog.record(auth.getName(), "CREATE", "YeuCauBaoTri", savedMaintenance.getId(), null,
                savedMaintenance.getTieuDe(), "Tạo từ đợt kiểm kê " + session.getName());
        return Map.of("maintenanceRequestId", savedMaintenance.getId());
    }

    @GetMapping("/kiem-ke/{id}/variance.csv")
    @PreAuthorize("hasRole('ADMIN') or @stocktakeAuthorization.canAccess(#p0, authentication)")
    @Transactional(readOnly = true)
    public ResponseEntity<String> stocktakeVariance(@PathVariable Integer id) {
        stocktakeRepository.findById(id).orElseThrow(() -> notFound("Không tìm thấy đợt kiểm kê"));
        StringBuilder csv = new StringBuilder("\uFEFFnhom,maTaiSan,tenTaiSan,viTriTheoSo,nguoiGiuTheoSo,viTriThucTe,tinhTrang,ghiChu\r\n");
        for (StocktakeItem item : stocktakeItemRepository.findBySession_IdOrderByIdAsc(id)) {
            String status = canonicalStocktakeStatus(item.getConditionStatus());
            if ("CO_MAT".equals(status) || item.getCheckedAt() == null) continue;
            csv.append(csv(status)).append(',')
                    .append(csv(item.getAsset().getMaTaiSan())).append(',')
                    .append(csv(item.getAsset().getTenTaiSan())).append(',')
                    .append(csv(item.getBookLocation())).append(',')
                    .append(csv(item.getBookHolder())).append(',')
                    .append(csv(item.getActualLocation())).append(',')
                    .append(csv(status)).append(',')
                    .append(csv(item.getNotes())).append("\r\n");
        }
        for (StocktakeExtra extra : stocktakeExtraRepository.findBySession_IdOrderByIdAsc(id)) {
            csv.append(csv("THUA")).append(',')
                    .append(csv(extra.getAssetCode())).append(',')
                    .append(csv(extra.getAssetName())).append(',')
                    .append(csv("")).append(',')
                    .append(csv("")).append(',')
                    .append(csv(extra.getActualLocation())).append(',')
                    .append(csv("THUA")).append(',')
                    .append(csv(extra.getNotes())).append("\r\n");
        }
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"stocktake-" + id + "-variance.csv\"")
                .body(csv.toString());
    }

    @GetMapping("/kiem-ke/{id}/report.pdf")
    @PreAuthorize("hasRole('ADMIN') or @stocktakeAuthorization.canAccess(#p0, authentication)")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> stocktakeReport(@PathVariable Integer id) {
        StocktakeSession session = stocktakeRepository.findById(id).orElseThrow(() -> notFound("Không tìm thấy đợt kiểm kê"));
        if (!"COMPLETED".equals(session.getStatus()) || session.getReportPdf() == null) {
            throw conflict("Biên bản PDF chỉ có sau khi hoàn thành đợt kiểm kê");
        }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"bien-ban-kiem-ke-" + id + ".pdf\"")
                .body(session.getReportPdf());
    }

    @GetMapping("/dashboard")
    @Transactional(readOnly = true)
    public Dashboard dashboard(
            @RequestParam(required = false) String tinhTrang,
            @RequestParam(required = false) String loaiTaiSan,
            @RequestParam(required = false) String phongBan,
            @RequestParam(required = false) LocalDate muaTuNgay,
            @RequestParam(required = false) LocalDate muaDenNgay) {
        Set<String> statuses = Set.of("Trong kho", "Đang sử dụng", "Bảo trì", "Chờ thanh lý",
                "Thanh lý", "Đã thanh lý", "Thiếu");
        if (tinhTrang != null && !tinhTrang.isBlank() && !statuses.contains(tinhTrang)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tình trạng tài sản không hợp lệ");
        }
        if (loaiTaiSan != null && loaiTaiSan.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tên loại tài sản tối đa 100 ký tự");
        }
        if (phongBan != null && phongBan.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tên phòng ban tối đa 100 ký tự");
        }
        if (muaTuNgay != null && muaDenNgay != null && muaTuNgay.isAfter(muaDenNgay)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ngày mua bắt đầu không được sau ngày kết thúc");
        }
        String categoryFilter = loaiTaiSan == null || loaiTaiSan.isBlank() ? null : loaiTaiSan.trim();
        String departmentFilter = phongBan == null || phongBan.isBlank() ? null : phongBan.trim();
        List<TaiSan> allAssets = assetRepository.findAll();
        List<com.example.quanlytaisan.dto.MaintenanceScheduleDtos.Response> schedules =
                maintenanceScheduleService.schedules();
        Set<Integer> scheduledAssetIds = schedules.stream()
                .map(com.example.quanlytaisan.dto.MaintenanceScheduleDtos.Response::taiSanId)
                .collect(Collectors.toSet());
        Map<Integer, TaiSanHolderInfo> holders = allAssets.isEmpty() ? Map.of()
                : assignmentRepository.findCurrentHolders(allAssets.stream().map(TaiSan::getTaiSanId).toList()).stream()
                .collect(Collectors.toMap(TaiSanHolderInfo::getTaiSanId, Function.identity()));
        List<TaiSan> assets = allAssets.stream()
                .filter(asset -> tinhTrang == null || tinhTrang.isBlank() || tinhTrang.equals(asset.getTinhTrang()))
                .filter(asset -> categoryFilter == null || categoryFilter.equals(
                        asset.getLoaiTaiSan() == null ? "Chưa phân loại" : asset.getLoaiTaiSan().getTenLoai()))
                .filter(asset -> departmentFilter == null || Optional.ofNullable(holders.get(asset.getTaiSanId()))
                        .map(TaiSanHolderInfo::getPhongBan).filter(Objects::nonNull)
                        .map(department -> department.toLowerCase(Locale.ROOT)
                                .contains(departmentFilter.toLowerCase(Locale.ROOT))).orElse(false))
                .filter(asset -> muaTuNgay == null || asset.getNgayMua() != null
                        && !asset.getNgayMua().isBefore(muaTuNgay))
                .filter(asset -> muaDenNgay == null || asset.getNgayMua() != null
                        && !asset.getNgayMua().isAfter(muaDenNgay))
                .toList();
        Set<Integer> filteredAssetIds = assets.stream().map(TaiSan::getTaiSanId).collect(Collectors.toSet());
        Map<String, Long> statusCounts = assets.stream().collect(Collectors.groupingBy(TaiSan::getTinhTrang, TreeMap::new, Collectors.counting()));
        Map<String, Long> categoryCounts = assets.stream().collect(Collectors.groupingBy(
                a -> a.getLoaiTaiSan() == null ? "Chưa phân loại" : a.getLoaiTaiSan().getTenLoai(),
                TreeMap::new, Collectors.counting()));
        Map<String, Long> departmentCounts = new TreeMap<>();
        for (TaiSan asset : assets) {
            TaiSanHolderInfo holder = holders.get(asset.getTaiSanId());
            if (holder != null && holder.getPhongBan() != null && !holder.getPhongBan().isBlank()) {
                departmentCounts.merge(holder.getPhongBan(), 1L, Long::sum);
            }
        }
        BigDecimal total = assets.stream().map(TaiSan::getGiaMua).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remaining = assets.stream().map(this::remainingValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Alert> alerts = new ArrayList<>();
        long maintenanceDueSoon = 0;
        long maintenanceOverdue = 0;
        for (var schedule : schedules) {
            if (filteredAssetIds.contains(schedule.taiSanId())) {
                if ("Sắp đến hạn".equals(schedule.trangThai())) maintenanceDueSoon++;
                if ("Đến hạn".equals(schedule.trangThai())) maintenanceDueSoon++;
                if ("Quá hạn".equals(schedule.trangThai())) maintenanceOverdue++;
            }
            if (Set.of("Sắp đến hạn", "Đến hạn", "Quá hạn").contains(schedule.trangThai())) {
                String dueLabel = schedule.trangThai();
                alerts.add(new Alert("MAINTENANCE", dueLabel + " bảo trì: " + schedule.tenTaiSan(),
                        "Hạn bảo trì " + schedule.ngayDenHanTiepTheo()
                                + (schedule.nguoiDangGiu() == null ? "" : " · Người giữ " + schedule.nguoiDangGiu()),
                        "Quá hạn".equals(dueLabel) ? "HIGH" : "MEDIUM",
                        "/admin/maintenance#schedule-" + schedule.id()));
            }
        }
        for (Consumable item : consumableRepository.findAll()) {
            if (item.isActive() && (item.getStockQuantity().compareTo(BigDecimal.ZERO) <= 0
                    || item.getStockQuantity().compareTo(item.getMinimumStock()) < 0)) {
                boolean exhausted = item.getStockQuantity().compareTo(BigDecimal.ZERO) <= 0;
                alerts.add(new Alert("LOW_STOCK", (exhausted ? "Vật tư đã hết: " : "Vật tư sắp hết: ") + item.getName(),
                        "Tồn kho " + item.getStockQuantity() + " " + item.getUnit() + ", mức tối thiểu " + item.getMinimumStock(),
                        exhausted ? "HIGH" : "MEDIUM", "/admin/consumables"));
            }
        }
        for (TaiSan asset : allAssets) {
            if (asset.getWarrantyEndDate() != null && !asset.getWarrantyEndDate().isAfter(LocalDate.now().plusDays(30))) {
                alerts.add(new Alert("WARRANTY", "Bảo hành sắp hết hạn: " + asset.getTenTaiSan(),
                        "Hết hạn ngày " + asset.getWarrantyEndDate(),
                        asset.getWarrantyEndDate().isBefore(LocalDate.now()) ? "HIGH" : "LOW", "/admin/assets"));
            }
        }
        LocalDate today = LocalDate.now();
        List<TaiSanCapPhat> activeAssignments = assignmentRepository.findByThuHoiLucIsNullOrderByIdDesc();
        for (TaiSanCapPhat assignment : activeAssignments) {
            LocalDate expected = assignment.getNgayDuKienThuHoi();
            if (expected != null && expected.isBefore(today)) {
                alerts.add(new Alert("OVERDUE_RETURN", "Quá hạn thu hồi: " + assignment.getTaiSan().getTenTaiSan(),
                        "Người nhận " + assignment.getNguoiNhan() + ", dự kiến thu hồi ngày " + expected, "HIGH", "/admin/assignments#returns"));
            }
        }
        for (EmployeeResponse employee : employeeRepository.findAll().stream().map(this::employeeResponse).toList()) {
            if (!isInactive(employee.trangThai())) continue;
            List<TaiSanCapPhat> heldAssets = matchingAssignments(activeAssignments, employee);
            if (heldAssets.isEmpty()) continue;
            String assetNames = heldAssets.stream().limit(3).map(assignment -> assignment.getTaiSan().getTenTaiSan())
                    .collect(Collectors.joining(", "));
            if (heldAssets.size() > 3) assetNames += ", ...";
            alerts.add(new Alert("EMPLOYEE_RETURN", "Cần thu hồi tài sản của nhân viên đã nghỉ việc: " + employee.hoTen(),
                    heldAssets.size() + " tài sản đang giao: " + assetNames, "HIGH", "/admin/assignments"));
        }
        if (!allAssets.isEmpty()) {
            Map<Integer, BaoTriTongHop> maintenance = maintenanceRepository.summarizeCompletedByTaiSanIds(
                    allAssets.stream().map(TaiSan::getTaiSanId).toList()).stream()
                    .collect(Collectors.toMap(BaoTriTongHop::getTaiSanId, Function.identity()));
            for (TaiSan asset : allAssets) {
                if (scheduledAssetIds.contains(asset.getTaiSanId())) continue;
                BaoTriTongHop summary = maintenance.get(asset.getTaiSanId());
                LocalDate lastService = summary == null || summary.getBaoTriGanNhat() == null
                        ? asset.getNgayMua()
                        : summary.getBaoTriGanNhat().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
                if (lastService == null) continue;
                LocalDate due = lastService.plusMonths(asset.getChuKyBaoTriThang());
                if (due.isBefore(today)) {
                    if (filteredAssetIds.contains(asset.getTaiSanId())) maintenanceOverdue++;
                    alerts.add(new Alert("MAINTENANCE", "Quá hạn bảo trì: " + asset.getTenTaiSan(),
                            "Hạn bảo trì " + due, "HIGH", "/admin/maintenance"));
                } else if (!due.isAfter(today.plusDays(30))) {
                    if (filteredAssetIds.contains(asset.getTaiSanId())) maintenanceDueSoon++;
                    alerts.add(new Alert("MAINTENANCE", "Sắp đến hạn bảo trì: " + asset.getTenTaiSan(),
                            "Hạn bảo trì " + due, "MEDIUM", "/admin/maintenance"));
                }
            }
        }
        return new Dashboard(assets.size(), statusCounts.getOrDefault("Đang sử dụng", 0L),
                statusCounts.getOrDefault("Trong kho", 0L), statusCounts.getOrDefault("Bảo trì", 0L),
                statusCounts.getOrDefault("Thanh lý", 0L) + statusCounts.getOrDefault("Đã thanh lý", 0L),
                statusCounts.getOrDefault("Chờ thanh lý", 0L),
                statusCounts.getOrDefault("Thiếu", 0L),
                maintenanceDueSoon, maintenanceOverdue,
                total, remaining, filteredAssetIds.isEmpty() ? BigDecimal.ZERO
                        : maintenanceRepository.repairCostByAssetIds(new ArrayList<>(filteredAssetIds)),
                metrics(statusCounts), metrics(categoryCounts), metrics(departmentCounts), alerts);
    }

    private List<Metric> metrics(Map<String, Long> source) {
        return source.entrySet().stream().map(e -> new Metric(e.getKey(), e.getValue())).toList();
    }
    private List<TaiSanCapPhat> assignmentsForEmployee(EmployeeResponse employee) {
        return matchingAssignments(assignmentRepository.findByThuHoiLucIsNullOrderByIdDesc(), employee);
    }

    private List<TaiSanCapPhat> matchingAssignments(List<TaiSanCapPhat> assignments, EmployeeResponse employee) {
        return assignments.stream()
                .filter(assignment -> samePerson(assignment.getNguoiNhan(), employee.hoTen())
                        || samePerson(assignment.getNguoiNhan(), employee.maNhanVien()))
                .toList();
    }

    private boolean samePerson(String left, String right) {
        return left != null && right != null && left.trim().equalsIgnoreCase(right.trim());
    }

    private boolean isInactive(String status) {
        return "INACTIVE".equalsIgnoreCase(status) || "Đã nghỉ việc".equalsIgnoreCase(status);
    }

    private BigDecimal remainingValue(TaiSan asset) {
        if (isDisposed(asset.getTinhTrang())) {
            return BigDecimal.ZERO;
        }
        BigDecimal cost = asset.getGiaMua();
        if (cost == null || asset.getNgayMua() == null) return BigDecimal.ZERO;
        int life = Optional.ofNullable(effectiveUsefulLife(asset)).orElse(5);
        if (life <= 0) return BigDecimal.ZERO;
        long daysElapsed = Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(asset.getNgayMua(), LocalDate.now()));
        long usefulLifeDays = java.time.temporal.ChronoUnit.DAYS.between(asset.getNgayMua(),
                asset.getNgayMua().plusYears(life));
        BigDecimal depreciation = cost.multiply(BigDecimal.valueOf(daysElapsed))
                .divide(BigDecimal.valueOf(usefulLifeDays), 10, RoundingMode.HALF_UP);
        return cost.subtract(depreciation).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }
    private void validateEmployee(EmployeeRequest r, Integer id) {
        if (r == null || blank(r.maNhanVien()) || r.maNhanVien().trim().length() > 30 || blank(r.hoTen()) || r.hoTen().trim().length() > 150)
            throw bad("Mã nhân viên và họ tên là bắt buộc (tối đa 30/150 ký tự)");
        if (id == null ? employeeRepository.existsByEmployeeCodeIgnoreCase(r.maNhanVien().trim())
                : employeeRepository.existsByEmployeeCodeIgnoreCaseAndIdNot(r.maNhanVien().trim(), id))
            throw conflict("Mã nhân viên đã tồn tại");
        if (blank(r.trangThai()) || !Set.of("ACTIVE", "INACTIVE", "Đang làm việc", "Đã nghỉ việc").contains(r.trangThai()))
            throw bad("Trạng thái nhân viên không hợp lệ");
        if (!blank(r.phongBan()) && masterRepository.findByKindOrderByNameAsc(DEPARTMENT).stream()
                .noneMatch(d -> d.getName().equalsIgnoreCase(r.phongBan().trim()) || d.getCode().equalsIgnoreCase(r.phongBan().trim())))
            throw bad("Phòng ban không tồn tại");
    }
    private void applyEmployee(AdminEmployee e, EmployeeRequest r) {
        e.setEmployeeCode(r.maNhanVien().trim()); e.setFullName(r.hoTen().trim());
        e.setDepartmentId(blank(r.phongBan()) ? null : masterRepository.findByKindOrderByNameAsc(DEPARTMENT).stream()
                .filter(d -> d.getName().equalsIgnoreCase(r.phongBan().trim()) || d.getCode().equalsIgnoreCase(r.phongBan().trim()))
                .map(AdminMasterData::getId).findFirst().orElse(null));
        e.setTitle(trim(r.chucVu())); e.setEmploymentStatus(blank(r.trangThai()) ? "ACTIVE" : r.trangThai().trim());
    }
    private EmployeeResponse employeeResponse(AdminEmployee e) {
        return new EmployeeResponse(e.getId(), e.getEmployeeCode(), e.getFullName(), departmentName(e.getDepartmentId()), e.getTitle(), e.getEmploymentStatus());
    }
    private String departmentName(Integer id) {
        if (id == null) return null;
        return masterRepository.findById(id).filter(m -> DEPARTMENT.equals(m.getKind())).map(AdminMasterData::getName).orElse(null);
    }
    private DepartmentResponse departmentResponse(AdminMasterData m) { return new DepartmentResponse(m.getId(), m.getCode(), m.getName()); }
    private LocationResponse locationResponse(AdminMasterData m) { return new LocationResponse(m.getId(), m.getBuilding(), m.getFloor(), m.getRoom()); }
    private SupplierResponse supplierResponse(AdminMasterData m) {
        return new SupplierResponse(m.getId(), m.getName(), m.getContactName(), m.getPhone(), m.getEmail(), m.getAddress());
    }
    private ConsumableResponse consumableResponse(Consumable c) {
        return new ConsumableResponse(c.getId(), c.getCode(), c.getName(), c.getUnit(), c.getStockQuantity(), c.getMinimumStock(), c.getUnitPrice(), c.isActive());
    }
    private IssueResponse issueResponse(ConsumableIssue i) {
        return issueResponse(i, i.getDepartmentName() == null ? departmentName(i.getDepartmentId()) : i.getDepartmentName());
    }
    private IssueResponse issueResponse(ConsumableIssue i, String dept) {
        return new IssueResponse(i.getId(), i.getConsumable().getId(), i.getConsumable().getName(), i.getDepartmentId(), dept,
                i.getQuantity(), i.getNotes(), i.getIssuedBy(), i.getIssuedAt(), i.getRecipient(), i.getIssueDate());
    }
    private ConsumableReceiptResponse consumableReceiptResponse(ConsumableReceipt receipt) {
        return new ConsumableReceiptResponse(receipt.getId(), receipt.getConsumable().getId(),
                receipt.getConsumable().getCode(), receipt.getConsumable().getName(), receipt.getQuantity(),
                receipt.getUnitPrice(), receipt.getSupplierId(), receipt.getSupplierName(),
                receipt.getReceivedOn(), receipt.getReceiptNumber(), receipt.getPurchaseReference(), receipt.getReceivedBy());
    }
    private ConsumableMovementResponse receiptMovement(ConsumableReceipt receipt) {
        return new ConsumableMovementResponse("NHAP", receipt.getId(), receipt.getConsumable().getId(),
                receipt.getConsumable().getCode(), receipt.getConsumable().getName(), receipt.getQuantity(),
                receipt.getReceivedOn(), null, null, receipt.getSupplierName(), receipt.getReceiptNumber(),
                receipt.getPurchaseReference(), receipt.getUnitPrice(), receipt.getReceivedBy(), null);
    }
    private ConsumableMovementResponse issueMovement(ConsumableIssue issue) {
        return new ConsumableMovementResponse("XUAT", issue.getId(), issue.getConsumable().getId(),
                issue.getConsumable().getCode(), issue.getConsumable().getName(), issue.getQuantity(),
                issue.getIssueDate(), issue.getDepartmentName() == null ? departmentName(issue.getDepartmentId()) : issue.getDepartmentName(),
                issue.getRecipient(), null, null, null, null, issue.getIssuedBy(), issue.getNotes());
    }
    private ConsumableRequestResponse consumableRequestResponse(com.example.quanlytaisan.entity.ConsumableRequest request) {
        return new ConsumableRequestResponse(request.getId(), request.getConsumable().getId(),
                request.getConsumable().getCode(), request.getConsumable().getName(), request.getConsumable().getUnit(),
                request.getQuantity(), request.getReason(), request.getRequester(), request.getDepartmentName(),
                request.getStatus(), request.getReviewer(), request.getReviewNote(), request.getCreatedAt(), request.getReviewedAt());
    }
    private InvoiceResponse invoiceResponse(AdminInvoice invoice) {
        BigDecimal paid = totalPaid(invoice);
        List<InvoiceLineResponse> lines = invoiceLineRepository.findByInvoice_IdOrderByIdAsc(invoice.getId())
                .stream().map(line -> new InvoiceLineResponse(line.getId(), line.getItemType(), line.getItemName(),
                        line.getQuantity(), line.getUnitPrice(),
                        line.getQuantity().multiply(line.getUnitPrice()).setScale(2, RoundingMode.HALF_UP),
                        line.getAssetId(), line.getConsumableId(), line.getPurchaseOrderItemId())).toList();
        List<InvoicePaymentResponse> payments = invoicePaymentRepository
                .findByInvoice_IdOrderByPaymentDateAscIdAsc(invoice.getId()).stream()
                .map(payment -> new InvoicePaymentResponse(payment.getId(), payment.getPaymentDate(),
                        payment.getAmount(), payment.getMethod(), payment.getRecordedBy(), payment.getCreatedAt())).toList();
        List<InvoiceAdjustmentResponse> adjustments = invoiceAdjustmentRepository
                .findByInvoice_IdOrderByAdjustmentDateAscIdAsc(invoice.getId()).stream()
                .map(adjustment -> new InvoiceAdjustmentResponse(adjustment.getId(), adjustment.getAdjustmentDate(),
                        adjustment.getAmountDelta(), adjustment.getReason(), adjustment.getRecordedBy(),
                        adjustment.getCreatedAt())).toList();
        String supplierName = invoice.getSupplierId() == null ? invoice.getSupplierName()
                : masterRepository.findById(invoice.getSupplierId()).map(AdminMasterData::getName)
                        .orElse(invoice.getSupplierName());
        String paymentStatus = paid.compareTo(BigDecimal.ZERO) == 0 ? "CHUA_TRA"
                : paid.compareTo(invoice.getTotalAmount()) >= 0 ? "DA_TRA" : "TRA_MOT_PHAN";
        return new InvoiceResponse(invoice.getId(), invoice.getInvoiceNumber(), invoice.getInvoiceDate(),
                invoice.getTotalAmount(), invoice.getPurchaseAssetId(), invoice.getSupplierId(), supplierName,
                invoice.getPurchaseOrderId(), paymentStatus, paid,
                invoice.getTotalAmount().subtract(paid).max(BigDecimal.ZERO), invoice.getAttachmentName(),
                invoice.getDescription(), lines, payments, adjustments);
    }

    private BigDecimal validateAndAddInvoiceLine(AdminInvoice invoice, InvoiceLineRequest request,
            LocalDate invoiceDate, Integer supplierId) {
        return validateAndAddInvoiceLine(invoice, request, invoiceDate, supplierId, null);
    }

    private BigDecimal validateAndAddInvoiceLine(AdminInvoice invoice, InvoiceLineRequest request,
            LocalDate invoiceDate, Integer supplierId, Integer purchaseOrderItemId) {
        if (request == null || blank(request.ten()) || request.ten().trim().length() > 200
                || request.soLuong() == null || request.soLuong().compareTo(BigDecimal.ZERO) <= 0
                || request.donGia() == null || request.donGia().compareTo(BigDecimal.ZERO) < 0
                || request.soLuong().scale() > 3 || request.donGia().scale() > 2) {
            throw bad("Dòng hóa đơn cần tên hàng, số lượng dương (tối đa 3 chữ số thập phân) và đơn giá hợp lệ");
        }
        String type = blank(request.loai()) ? (request.taiSanId() != null ? "TAI_SAN"
                : request.vatTuId() != null ? "VAT_TU" : "KHAC") : request.loai().trim().toUpperCase(Locale.ROOT);
        if (!Set.of("TAI_SAN", "VAT_TU", "KHAC").contains(type)) throw bad("Loại dòng hóa đơn không hợp lệ");
        if (request.taiSanId() != null && request.vatTuId() != null) {
            throw bad("Một dòng hóa đơn không thể liên kết đồng thời tài sản và vật tư");
        }
        if (request.taiSanId() != null && request.soLuong().compareTo(BigDecimal.ONE) != 0) {
            throw bad("Dòng liên kết trực tiếp với tài sản phải có số lượng bằng 1");
        }
        if (request.taiSanId() != null) {
            TaiSan asset = assetRepository.findById(request.taiSanId())
                    .orElseThrow(() -> bad("Tài sản liên kết không tồn tại"));
            if (request.donGia().compareTo(BigDecimal.ZERO) > 0) asset.setGiaMua(request.donGia());
            asset.setNgayMua(invoiceDate);
            if (supplierId != null) asset.setSupplierId(supplierId);
            assetRepository.save(asset);
        }
        if (request.vatTuId() != null && !consumableRepository.existsById(request.vatTuId())) {
            throw bad("Vật tư liên kết không tồn tại");
        }
        BigDecimal quantity = request.soLuong().stripTrailingZeros();
        BigDecimal unitPrice = request.donGia().setScale(2, RoundingMode.HALF_UP);
        AdminInvoiceLine line = new AdminInvoiceLine();
        line.setInvoice(invoice);
        line.setItemType(type);
        line.setItemName(request.ten().trim());
        line.setQuantity(quantity);
        line.setUnitPrice(unitPrice);
        line.setAssetId(request.taiSanId());
        line.setConsumableId(request.vatTuId());
        line.setPurchaseOrderItemId(purchaseOrderItemId);
        invoice.getInvoiceLines().add(line);
        return quantity.multiply(unitPrice);
    }

    private void validateInvoiceNumber(String invoiceNumber) {
        if (blank(invoiceNumber) || invoiceNumber.trim().length() > 50) {
            throw bad("Số hóa đơn là bắt buộc và tối đa 50 ký tự");
        }
        if (invoiceRepository.existsByInvoiceNumberIgnoreCase(invoiceNumber.trim())) {
            throw conflict("Số hóa đơn đã tồn tại");
        }
    }

    private void validateSupplier(Integer supplierId) {
        if (supplierId != null && !masterRepository.existsByKindAndId(SUPPLIER, supplierId)) {
            throw bad("Nhà cung cấp không tồn tại");
        }
    }

    private void validateInvoiceDescription(String description) {
        if (description != null && description.trim().length() > 500) {
            throw bad("Ghi chú hóa đơn tối đa 500 ký tự");
        }
    }

    private BigDecimal totalPaid(AdminInvoice invoice) {
        return invoicePaymentRepository.findByInvoice_IdOrderByPaymentDateAscIdAsc(invoice.getId()).stream()
                .map(AdminInvoicePayment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal validateInvoiceTotal(BigDecimal total) {
        BigDecimal normalized = total.setScale(2, RoundingMode.HALF_UP);
        if (normalized.compareTo(BigDecimal.ZERO) <= 0
                || normalized.compareTo(new BigDecimal("99999999999999.99")) > 0) {
            throw bad("Tổng tiền hóa đơn phải lớn hơn 0 và không vượt 99.999.999.999.999,99 VNĐ");
        }
        return normalized;
    }

    private void refreshPaymentStatus(AdminInvoice invoice) {
        BigDecimal paid = totalPaid(invoice);
        invoice.setPaymentStatus(paid.compareTo(BigDecimal.ZERO) == 0 ? "CHUA_TRA"
                : paid.compareTo(invoice.getTotalAmount()) >= 0 ? "DA_TRA" : "TRA_MOT_PHAN");
    }

    private boolean validInvoiceAttachment(String fileName, String contentType, byte[] data) {
        if (fileName == null || data == null || data.length == 0 || data.length > 10 * 1024 * 1024
                || contentType == null) return false;
        String lowerName = fileName.toLowerCase(Locale.ROOT);
        if (contentType.equalsIgnoreCase("application/pdf")) {
            return lowerName.endsWith(".pdf") && data.length >= 5
                    && data[0] == '%' && data[1] == 'P' && data[2] == 'D' && data[3] == 'F' && data[4] == '-';
        }
        if (contentType.equalsIgnoreCase("image/png")) {
            return lowerName.endsWith(".png") && data.length >= 8
                    && (data[0] & 0xff) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G';
        }
        return contentType.equalsIgnoreCase("image/jpeg") && (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg"))
                && data.length >= 3 && (data[0] & 0xff) == 0xff && (data[1] & 0xff) == 0xd8
                && (data[2] & 0xff) == 0xff;
    }

    private Integer effectiveUsefulLife(TaiSan asset) {
        if (asset.getThoiGianKhauHaoNam() != null) return asset.getThoiGianKhauHaoNam();
        return asset.getLoaiTaiSan() == null ? null : asset.getLoaiTaiSan().getThoiGianKhauHaoNam();
    }

    private boolean isDisposed(String status) {
        return "Thanh lý".equalsIgnoreCase(status) || "Đã thanh lý".equalsIgnoreCase(status)
                || "DA_THANH_LY".equalsIgnoreCase(status);
    }
    private StocktakeResponse stocktakeResponse(StocktakeSession s) {
        return new StocktakeResponse(s.getId(), s.getName(), s.getDepartment(), s.getStocktakeDate(), s.getEndDate(),
                s.getStatus(), stocktakeItemRepository.countBySession_Id(s.getId()),
                stocktakeItemRepository.countBySession_IdAndCheckedAtIsNotNull(s.getId()),
                stocktakeExtraRepository.countBySession_Id(s.getId()), s.getResponsibleUsername(),
                s.getCheckerName(), s.getConfirmerName(), s.getScopeType(), s.getScopeValue());
    }
    private StocktakeItemResponse stocktakeItemResponse(StocktakeItem i) {
        return new StocktakeItemResponse(i.getId(), i.getAsset().getTaiSanId(), i.getAsset().getMaTaiSan(),
                i.getAsset().getTenTaiSan(), i.getBookLocation(), i.getBookHolder(), i.getActualLocation(),
                canonicalStocktakeStatus(i.getConditionStatus()), i.getNotes(), i.getCheckedBy(),
                i.getCheckedAt(), i.getMaintenanceRequestId());
    }
    private StocktakeExtraResponse stocktakeExtraResponse(StocktakeExtra extra) {
        return new StocktakeExtraResponse(extra.getId(), extra.getAssetCode(), extra.getAssetName(),
                extra.getActualLocation(), extra.getNotes(), extra.getCheckedBy(), extra.getCheckedAt());
    }
    private String assetLocation(TaiSan asset) {
        List<String> parts = new ArrayList<>();
        if (!blank(asset.getToaNha())) parts.add(asset.getToaNha());
        if (!blank(asset.getTang())) parts.add("Tầng " + asset.getTang());
        if (!blank(asset.getPhong())) parts.add("Phòng " + asset.getPhong());
        return parts.isEmpty() ? null : String.join(" · ", parts);
    }
    private void validateCodeName(String code, String name) {
        if (blank(code) || code.trim().length() > 50 || blank(name) || name.trim().length() > 150)
            throw bad("Mã và tên phòng ban là bắt buộc (tối đa 50/150 ký tự)");
    }
    private void ensureMasterUnique(String kind, String code, Integer id) {
        boolean duplicate = id == null ? masterRepository.existsByKindAndCodeIgnoreCase(kind, code.trim())
                : masterRepository.existsByKindAndCodeIgnoreCaseAndIdNot(kind, code.trim(), id);
        if (duplicate) throw conflict("Mã đã tồn tại");
    }
    private void validateLocation(LocationRequest r) {
        if (r == null || blank(r.toaNha()) || blank(r.phong()) || r.toaNha().length() > 100 || r.phong().length() > 100
                || (r.tang() != null && r.tang().length() > 30)) throw bad("Tòa nhà và phòng là bắt buộc, độ dài vượt giới hạn");
    }
    private void applyLocation(AdminMasterData m, LocationRequest r) {
        String code = locationKey(r);
        m.setKind(LOCATION); m.setCode(code); m.setName(code); m.setBuilding(r.toaNha().trim());
        m.setFloor(trim(r.tang())); m.setRoom(r.phong().trim());
    }
    private String locationKey(LocationRequest r) {
        return r.toaNha().trim() + "|" + (r.tang() == null ? "" : r.tang().trim()) + "|" + r.phong().trim();
    }
    private void validateSupplier(SupplierRequest r) {
        if (r == null || blank(r.tenNhaCungCap()) || r.tenNhaCungCap().trim().length() > 150) throw bad("Tên nhà cung cấp là bắt buộc");
        if (r.email() != null && !r.email().isBlank() && !r.email().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) throw bad("Email nhà cung cấp không hợp lệ");
    }
    private void applySupplier(AdminMasterData m, SupplierRequest r) {
        m.setKind(SUPPLIER); m.setName(r.tenNhaCungCap().trim()); m.setCode(r.tenNhaCungCap().trim());
        m.setContactName(trim(r.nguoiLienHe())); m.setPhone(trim(r.soDienThoai())); m.setEmail(trim(r.email())); m.setAddress(trim(r.diaChi()));
    }
    private void validateConsumable(AdminDtos.ConsumableRequest r, Integer id) {
        if (r == null || blank(r.maVatTu()) || blank(r.tenVatTu()) || blank(r.donVi())) throw bad("Mã, tên và đơn vị vật tư là bắt buộc");
        if (r.maVatTu().trim().length() > 40 || r.tenVatTu().trim().length() > 150 || r.donVi().trim().length() > 30) {
            throw bad("Mã tối đa 40, tên tối đa 150 và đơn vị tối đa 30 ký tự");
        }
        if (id == null ? consumableRepository.existsByCodeIgnoreCase(r.maVatTu().trim())
                : consumableRepository.existsByCodeIgnoreCaseAndIdNot(r.maVatTu().trim(), id)) throw conflict("Mã vật tư đã tồn tại");
        for (BigDecimal v : List.of(Optional.ofNullable(r.soLuongTon()).orElse(BigDecimal.ZERO),
                Optional.ofNullable(r.mucToiThieu()).orElse(BigDecimal.ZERO), Optional.ofNullable(r.donGia()).orElse(BigDecimal.ZERO)))
            if (v.compareTo(BigDecimal.ZERO) < 0 || v.scale() > 2
                    || v.compareTo(new BigDecimal("999999999999.99")) > 0) {
                throw bad("Số lượng, mức tồn và đơn giá phải không âm, tối đa 2 số thập phân");
            }
    }
    private void applyConsumable(Consumable c, AdminDtos.ConsumableRequest r) {
        c.setCode(r.maVatTu().trim()); c.setName(r.tenVatTu().trim()); c.setUnit(r.donVi().trim());
        c.setMinimumStock(Optional.ofNullable(r.mucToiThieu()).orElse(BigDecimal.ZERO));
        if (c.getId() == null || r.donGia() != null) c.setUnitPrice(Optional.ofNullable(r.donGia()).orElse(BigDecimal.ZERO));
        c.setActive(r.dangSuDung() == null ? true : r.dangSuDung());
    }
    private void validateStocktake(StocktakeRequest r) {
        if (r == null || blank(r.tenDot()) || r.tenDot().trim().length() > 100 || r.batDauLuc() == null
                || (!blank(r.trangThai()) && !STOCKTAKE_STATUSES.contains(r.trangThai().toUpperCase(Locale.ROOT))))
            throw bad("Tên đợt (tối đa 100 ký tự), ngày bắt đầu và trạng thái hợp lệ là bắt buộc");
        if (r.batDauLuc().isAfter(LocalDate.now())) throw bad("Ngày bắt đầu không được ở tương lai");
        if (r.ketThucLuc() != null && r.ketThucLuc().isBefore(r.batDauLuc())) throw bad("Ngày kết thúc không được trước ngày bắt đầu");
        if (r.ketThucLuc() != null && r.ketThucLuc().isBefore(LocalDate.now())) {
            throw bad("Ngày kết thúc dự kiến không được ở quá khứ");
        }
        if (r.nguoiPhuTrach() != null && r.nguoiPhuTrach().length() > 50) throw bad("Tên đăng nhập người phụ trách tối đa 50 ký tự");
        if (r.nguoiKiem() != null && r.nguoiKiem().length() > 150) throw bad("Tên người kiểm tối đa 150 ký tự");
        if (r.nguoiXacNhan() != null && r.nguoiXacNhan().length() > 150) throw bad("Tên người xác nhận tối đa 150 ký tự");
        String scopeType = normalizeStocktakeScopeType(r.loaiPhamVi(), r.phongBan());
        String scopeValue = normalizeStocktakeScopeValue(r.phamVi(), r.phongBan(), scopeType);
        if ("DEPARTMENT".equals(scopeType)) {
            boolean knownDepartment = masterRepository.findByKindOrderByNameAsc(DEPARTMENT).stream()
                    .anyMatch(department -> department.getName().equalsIgnoreCase(scopeValue));
            if (!knownDepartment) throw bad("Phòng ban kiểm kê không tồn tại");
        } else if ("LOCATION".equals(scopeType)) {
            boolean knownLocation = masterRepository.findByKindOrderByNameAsc(LOCATION).stream()
                    .map(this::locationResponse)
                    .map(this::locationLabel)
                    .anyMatch(location -> location.equalsIgnoreCase(scopeValue));
            if (!knownLocation) throw bad("Vị trí kiểm kê không tồn tại trong danh mục");
        }
    }
    private void applyStocktake(StocktakeSession s, StocktakeRequest r) {
        String scopeType = normalizeStocktakeScopeType(r.loaiPhamVi(), r.phongBan());
        String scopeValue = normalizeStocktakeScopeValue(r.phamVi(), r.phongBan(), scopeType);
        s.setName(r.tenDot().trim());
        s.setScopeType(scopeType);
        s.setScopeValue(scopeValue);
        s.setDepartment("DEPARTMENT".equals(scopeType) ? scopeValue : null);
        s.setStocktakeDate(r.batDauLuc());
        s.setEndDate(r.ketThucLuc());
        if (!blank(r.trangThai())) s.setStatus(r.trangThai().toUpperCase(Locale.ROOT));
    }
    private String normalizeStocktakeScopeType(String requestedType, String legacyDepartment) {
        if (blank(requestedType)) return blank(legacyDepartment) ? "ALL" : "DEPARTMENT";
        String normalized = requestedType.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("ALL", "DEPARTMENT", "LOCATION").contains(normalized)) {
            throw bad("Phạm vi kiểm kê phải là ALL, DEPARTMENT hoặc LOCATION");
        }
        return normalized;
    }
    private String normalizeStocktakeScopeValue(String requestedValue, String legacyDepartment, String scopeType) {
        String value = blank(requestedValue) ? trim(legacyDepartment) : requestedValue.trim();
        if ("ALL".equals(scopeType)) return null;
        if (blank(value) || value.length() > 250) throw bad("Cần chọn phạm vi kiểm kê hợp lệ");
        return value;
    }
    private String locationLabel(LocationResponse location) {
        List<String> parts = new ArrayList<>();
        if (!blank(location.toaNha())) parts.add(location.toaNha());
        if (!blank(location.tang())) parts.add("Tầng " + location.tang());
        if (!blank(location.phong())) parts.add("Phòng " + location.phong());
        return String.join(" · ", parts);
    }
    private void validateStocktakeItem(StocktakeItemRequest r, boolean requireAsset) {
        if (r == null || (requireAsset && r.taiSanId() == null) || blank(r.tinhTrangThucTe())
                || !ITEM_STATUSES.contains(r.tinhTrangThucTe().toUpperCase(Locale.ROOT)))
            throw bad("Tình trạng thực tế phải là CO_MAT, THIEU, HONG hoặc SAI_VI_TRI");
        if (r.ghiChu() != null && r.ghiChu().length() > 500) throw bad("Ghi chú kiểm kê tối đa 500 ký tự");
        if (r.viTriThucTe() != null && r.viTriThucTe().length() > 250) throw bad("Vị trí thực tế tối đa 250 ký tự");
    }
    private String normalizeStocktakeStatus(String input) {
        return switch (input.toUpperCase(Locale.ROOT)) {
            case "PRESENT" -> "CO_MAT";
            case "MISSING" -> "THIEU";
            case "DAMAGED" -> "HONG";
            case "WRONG_LOCATION" -> "SAI_VI_TRI";
            default -> input.toUpperCase(Locale.ROOT);
        };
    }
    private String canonicalStocktakeStatus(String input) {
        return normalizeStocktakeStatus(input);
    }
    private AdminMasterData master(String kind, Integer id) {
        return masterRepository.findById(id).filter(m -> kind.equals(m.getKind())).orElseThrow(() -> notFound("Không tìm thấy dữ liệu"));
    }
    private String csv(String input) {
        String value = input == null ? "" : input;
        if (value.matches("^[\\s]*[=+\\-@].*")) value = "'" + value;
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
    private boolean blank(String s) { return s == null || s.isBlank(); }
    private String trim(String s) { return s == null || s.isBlank() ? null : s.trim(); }
    private ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
    private ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
}
