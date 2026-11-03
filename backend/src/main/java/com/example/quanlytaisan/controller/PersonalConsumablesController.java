package com.example.quanlytaisan.controller;

import com.example.quanlytaisan.dto.AdminDtos.ConsumableRequestCreate;
import com.example.quanlytaisan.dto.AdminDtos.ConsumableRequestResponse;
import com.example.quanlytaisan.dto.AdminDtos.ConsumableStockResponse;
import com.example.quanlytaisan.entity.AdminMasterData;
import com.example.quanlytaisan.entity.AppUser;
import com.example.quanlytaisan.entity.Consumable;
import com.example.quanlytaisan.entity.ConsumableRequest;
import com.example.quanlytaisan.repository.AdminMasterDataRepository;
import com.example.quanlytaisan.repository.AppUserRepository;
import com.example.quanlytaisan.repository.ConsumableRepository;
import com.example.quanlytaisan.repository.ConsumableRequestRepository;
import com.example.quanlytaisan.service.ActivityLogService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/me/consumables")
@PreAuthorize("hasRole('USER')")
public class PersonalConsumablesController {
    private final ConsumableRepository consumableRepository;
    private final ConsumableRequestRepository requestRepository;
    private final AppUserRepository userRepository;
    private final AdminMasterDataRepository masterRepository;
    private final ActivityLogService activityLog;

    public PersonalConsumablesController(ConsumableRepository consumableRepository,
            ConsumableRequestRepository requestRepository, AppUserRepository userRepository,
            AdminMasterDataRepository masterRepository, ActivityLogService activityLog) {
        this.consumableRepository = consumableRepository;
        this.requestRepository = requestRepository;
        this.userRepository = userRepository;
        this.masterRepository = masterRepository;
        this.activityLog = activityLog;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<ConsumableStockResponse> availableStock() {
        return consumableRepository.findAllByOrderByNameAsc().stream().filter(Consumable::isActive)
                .map(item -> new ConsumableStockResponse(item.getId(), item.getCode(), item.getName(), item.getUnit(),
                        item.getStockQuantity(), item.getMinimumStock()))
                .toList();
    }

    @GetMapping("/requests")
    @Transactional(readOnly = true)
    public List<ConsumableRequestResponse> requests(Authentication auth) {
        return requestRepository.findTop100ByRequesterIgnoreCaseOrderByCreatedAtDesc(auth.getName())
                .stream().map(this::response).toList();
    }

    @PostMapping("/requests")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public ConsumableRequestResponse createRequest(@RequestBody ConsumableRequestCreate input, Authentication auth) {
        if (input == null || input.vatTuId() == null || input.soLuong() == null
                || input.soLuong().compareTo(BigDecimal.ZERO) <= 0 || input.soLuong().scale() > 2
                || input.lyDo() == null || input.lyDo().isBlank() || input.lyDo().trim().length() > 500) {
            throw bad("Chọn vật tư, số lượng lớn hơn 0 và lý do tối đa 500 ký tự là bắt buộc");
        }
        Consumable item = consumableRepository.findById(input.vatTuId())
                .filter(Consumable::isActive).orElseThrow(() -> bad("Vật tư không tồn tại hoặc đã ngừng sử dụng"));
        AppUser user = userRepository.findByUsernameIgnoreCase(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Không tìm thấy tài khoản"));
        Integer departmentId = user.getEmployee() == null ? null : user.getEmployee().getDepartmentId();
        String departmentName = departmentId == null ? null : masterRepository.findById(departmentId)
                .filter(row -> "DEPARTMENT".equals(row.getKind())).map(AdminMasterData::getName).orElse(null);

        ConsumableRequest request = new ConsumableRequest();
        request.setConsumable(item);
        request.setQuantity(input.soLuong());
        request.setReason(input.lyDo().trim());
        request.setRequester(auth.getName());
        request.setDepartmentId(departmentId);
        request.setDepartmentName(departmentName);
        ConsumableRequestResponse response = response(requestRepository.save(request));
        activityLog.record(auth.getName(), "CREATE", "ConsumableRequest", response.id(), null, response, null);
        return response;
    }

    private ConsumableRequestResponse response(ConsumableRequest request) {
        return new ConsumableRequestResponse(request.getId(), request.getConsumable().getId(),
                request.getConsumable().getCode(), request.getConsumable().getName(), request.getConsumable().getUnit(),
                request.getQuantity(), request.getReason(), request.getRequester(), request.getDepartmentName(),
                request.getStatus(), request.getReviewer(), request.getReviewNote(), request.getCreatedAt(),
                request.getReviewedAt());
    }

    private ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
