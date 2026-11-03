package com.example.quanlytaisan.controller;

import com.example.quanlytaisan.dto.*;
import com.example.quanlytaisan.service.TaiSanVongDoiService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.time.LocalDate;

@RestController
@RequestMapping("/api")
@PreAuthorize("hasRole('ADMIN')")
public class TaiSanVongDoiController {
    private final TaiSanVongDoiService service;

    public TaiSanVongDoiController(TaiSanVongDoiService service) {
        this.service = service;
    }

    @GetMapping("/cap-phat")
    public List<TaiSanCapPhatResponse> assignments(@RequestParam(defaultValue = "false") boolean activeOnly) {
        return service.assignments(activeOnly);
    }

    @PostMapping("/cap-phat")
    @ResponseStatus(HttpStatus.CREATED)
    public TaiSanCapPhatResponse assign(@RequestBody TaiSanCapPhatRequest request) {
        return service.assign(request);
    }

    @PostMapping("/cap-phat/{id}/thu-hoi")
    public TaiSanCapPhatResponse returnAsset(
            @PathVariable Integer id,
            @RequestBody(required = false) ThuHoiTaiSanRequest request) {
        return service.returnAsset(id, request);
    }

    @GetMapping("/cap-phat/{id}/bien-ban-thu-hoi.pdf")
    public ResponseEntity<byte[]> returnRecordPdf(@PathVariable Integer id) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"bien-ban-thu-hoi-" + id + ".pdf\"")
                .body(service.returnRecordPdf(id));
    }

    @PutMapping("/cap-phat/{id}/tai-khoan-nguoi-nhan")
    public TaiSanCapPhatResponse linkRecipientAccount(@PathVariable Integer id, @RequestBody AccountLinkRequest request) {
        return service.linkRecipientAccount(id, request == null ? null : request.username());
    }

    @GetMapping("/bao-tri")
    public List<YeuCauBaoTriResponse> maintenances(
            @RequestParam(defaultValue = "false") boolean activeOnly,
            @RequestParam(required = false) String trangThai,
            @RequestParam(required = false) String phongBan,
            @RequestParam(required = false) LocalDate tuNgay,
            @RequestParam(required = false) LocalDate denNgay) {
        return service.maintenances(activeOnly, trangThai, phongBan, tuNgay, denNgay);
    }

    @PostMapping("/bao-tri")
    @ResponseStatus(HttpStatus.CREATED)
    public YeuCauBaoTriResponse createMaintenance(@RequestBody YeuCauBaoTriRequest request) {
        return service.createMaintenance(request);
    }

    @PutMapping("/bao-tri/{id}")
    public YeuCauBaoTriResponse updateMaintenance(
            @PathVariable Integer id,
            @RequestBody CapNhatBaoTriRequest request) {
        return service.updateMaintenance(id, request);
    }

    public record AccountLinkRequest(String username) {}
}
