package com.example.quanlytaisan.controller;

import com.example.quanlytaisan.dto.AdminUserRequest;
import com.example.quanlytaisan.dto.AdminUserResponse;
import com.example.quanlytaisan.service.AdminUserService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {
    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public List<AdminUserResponse> listUsers() {
        return adminUserService.listUsers();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminUserResponse createUser(@RequestBody AdminUserRequest request, Authentication authentication) {
        return adminUserService.createUser(request, authentication.getName());
    }

    @PutMapping("/{id}")
    public AdminUserResponse updateUser(
            @PathVariable Integer id,
            @RequestBody AdminUserRequest request,
            Authentication authentication) {
        return adminUserService.updateUser(id, request, authentication.getName());
    }

    @PostMapping("/{id}/unlock")
    public AdminUserResponse unlockUser(@PathVariable Integer id, Authentication authentication) {
        return adminUserService.unlockUser(id, authentication.getName());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@PathVariable Integer id, Authentication authentication) {
        adminUserService.deleteUser(id, authentication.getName());
    }
}
