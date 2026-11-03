package com.example.quanlytaisan.controller;

import com.example.quanlytaisan.dto.MaintenanceScheduleDtos.CompleteRequest;
import com.example.quanlytaisan.dto.MaintenanceScheduleDtos.Request;
import com.example.quanlytaisan.dto.MaintenanceScheduleDtos.Response;
import com.example.quanlytaisan.dto.YeuCauBaoTriResponse;
import com.example.quanlytaisan.service.MaintenanceScheduleService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/maintenance-schedules")
@PreAuthorize("hasRole('ADMIN')")
public class MaintenanceScheduleController {
    private final MaintenanceScheduleService service;

    public MaintenanceScheduleController(MaintenanceScheduleService service) {
        this.service = service;
    }

    @GetMapping
    public List<Response> schedules() {
        return service.schedules();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Response create(@RequestBody Request request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public Response update(@PathVariable Integer id, @RequestBody Request request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/pause")
    public Response pause(@PathVariable Integer id) {
        return service.setPaused(id, true);
    }

    @PostMapping("/{id}/resume")
    public Response resume(@PathVariable Integer id) {
        return service.setPaused(id, false);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Integer id) {
        service.delete(id);
    }

    @PostMapping("/{id}/maintenance")
    @ResponseStatus(HttpStatus.CREATED)
    public YeuCauBaoTriResponse complete(@PathVariable Integer id, @RequestBody CompleteRequest request) {
        return service.complete(id, request);
    }

    @GetMapping("/{id}/history")
    public List<YeuCauBaoTriResponse> history(@PathVariable Integer id) {
        return service.history(id);
    }
}
