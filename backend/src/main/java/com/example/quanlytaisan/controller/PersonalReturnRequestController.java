package com.example.quanlytaisan.controller;

import com.example.quanlytaisan.entity.*;
import com.example.quanlytaisan.repository.*;
import com.example.quanlytaisan.service.ActivityLogService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/me/return-requests")
@PreAuthorize("hasRole('USER')")
public class PersonalReturnRequestController {
    private final AssetReturnRequestRepository requestRepository;
    private final TaiSanCapPhatRepository assignmentRepository;
    private final AppUserRepository userRepository;
    private final ActivityLogService activityLog;

    public PersonalReturnRequestController(AssetReturnRequestRepository requestRepository,
            TaiSanCapPhatRepository assignmentRepository, AppUserRepository userRepository,
            ActivityLogService activityLog) {
        this.requestRepository = requestRepository;
        this.assignmentRepository = assignmentRepository;
        this.userRepository = userRepository;
        this.activityLog = activityLog;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<Response> requests(Authentication authentication) {
        return requestRepository.findByRequesterIgnoreCaseOrderByCreatedAtDesc(authentication.getName())
                .stream().map(this::response).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Response create(@RequestBody CreateRequest request, Authentication authentication) {
        if (request == null || request.assignmentId() == null
                || request.reason() == null || request.reason().isBlank()
                || request.reason().trim().length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Chọn tài sản và nhập lý do yêu cầu thu hồi (tối đa 500 ký tự)");
        }
        String username = authentication.getName();
        TaiSanCapPhat assignment = assignmentRepository.findById(request.assignmentId())
                .filter(row -> row.getThuHoiLuc() == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy tài sản đang được bàn giao"));
        AppUser user = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Không tìm thấy tài khoản"));
        String fullName = user.getEmployee() == null ? user.getFullName() : user.getEmployee().getFullName();
        boolean assignedToUser = username.equalsIgnoreCase(assignment.getUsernameNguoiNhan())
                || (assignment.getUsernameNguoiNhan() == null
                    && (username.equalsIgnoreCase(assignment.getNguoiNhan())
                        || fullName != null && fullName.equalsIgnoreCase(assignment.getNguoiNhan())));
        if (!assignedToUser) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Chỉ có thể yêu cầu trả tài sản đang được bàn giao cho bạn");
        }
        if (requestRepository.existsByAssignment_IdAndRequesterIgnoreCaseAndStatus(
                assignment.getId(), username, "PENDING")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Tài sản này đã có yêu cầu trả đang chờ xử lý");
        }

        AssetReturnRequest row = new AssetReturnRequest();
        row.setAssignment(assignment);
        row.setRequester(username);
        row.setReason(request.reason().trim());
        Response response = response(requestRepository.save(row));
        activityLog.record(username, "CREATE", "AssetReturnRequest", response.id(), null, response, null);
        return response;
    }

    private Response response(AssetReturnRequest row) {
        TaiSan asset = row.getAssignment().getTaiSan();
        return new Response(row.getId(), row.getAssignment().getId(), asset.getTaiSanId(),
                asset.getMaTaiSan(), asset.getTenTaiSan(), row.getReason(), row.getRequester(),
                row.getStatus(), row.getCreatedAt(), row.getCompletedAt());
    }

    public record CreateRequest(Integer assignmentId, String reason) {}
    public record Response(Integer id, Integer assignmentId, Integer taiSanId, String maTaiSan,
            String tenTaiSan, String reason, String requester, String status,
            Instant createdAt, Instant completedAt) {}
}
