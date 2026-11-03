package com.example.quanlytaisan.controller;

import com.example.quanlytaisan.entity.AssetReturnRequest;
import com.example.quanlytaisan.entity.TaiSan;
import com.example.quanlytaisan.repository.AssetReturnRequestRepository;
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
@RequestMapping("/api/admin/return-requests")
@PreAuthorize("hasRole('ADMIN')")
public class AdminReturnRequestController {
    private final AssetReturnRequestRepository requestRepository;
    private final ActivityLogService activityLog;

    public AdminReturnRequestController(AssetReturnRequestRepository requestRepository,
            ActivityLogService activityLog) {
        this.requestRepository = requestRepository;
        this.activityLog = activityLog;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<Response> requests() {
        return requestRepository.findAllByOrderByCreatedAtDesc().stream().map(this::response).toList();
    }

    @PutMapping("/{id}/complete")
    @Transactional
    public Response complete(@PathVariable Integer id, Authentication authentication) {
        AssetReturnRequest row = requestRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy yêu cầu trả tài sản"));
        if (row.getAssignment().getThuHoiLuc() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Hãy lập phiếu thu hồi tài sản trước khi hoàn tất yêu cầu");
        }
        if (!"COMPLETED".equals(row.getStatus())) {
            var previous = response(row);
            row.setStatus("COMPLETED");
            row.setCompletedAt(Instant.now());
            row = requestRepository.save(row);
            var updated = response(row);
            activityLog.record(authentication.getName(), "COMPLETE", "AssetReturnRequest", id,
                    previous, updated, "Yêu cầu trả được hoàn tất sau khi lập phiếu thu hồi.");
        }
        return response(row);
    }

    private Response response(AssetReturnRequest row) {
        TaiSan asset = row.getAssignment().getTaiSan();
        return new Response(row.getId(), row.getAssignment().getId(), asset.getTaiSanId(),
                asset.getMaTaiSan(), asset.getTenTaiSan(), row.getReason(), row.getRequester(),
                row.getStatus(), row.getCreatedAt(), row.getCompletedAt(),
                row.getAssignment().getThuHoiLuc() != null);
    }

    public record Response(Integer id, Integer assignmentId, Integer taiSanId, String maTaiSan,
            String tenTaiSan, String reason, String requester, String status,
            Instant createdAt, Instant completedAt, boolean returned) {}
}
