package com.example.quanlytaisan.controller;

import com.example.quanlytaisan.entity.AppUser;
import com.example.quanlytaisan.entity.AssetReturnRequest;
import com.example.quanlytaisan.entity.TaiSan;
import com.example.quanlytaisan.entity.TaiSanCapPhat;
import com.example.quanlytaisan.repository.AppUserRepository;
import com.example.quanlytaisan.repository.AssetReturnRequestRepository;
import com.example.quanlytaisan.repository.TaiSanCapPhatRepository;
import com.example.quanlytaisan.service.ActivityLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PersonalReturnRequestWorkflowTest {
    private AssetReturnRequestRepository requestRepository;
    private TaiSanCapPhatRepository assignmentRepository;
    private AppUserRepository userRepository;
    private PersonalReturnRequestController controller;
    private Authentication authentication;
    private TaiSanCapPhat assignment;

    @BeforeEach
    void setUp() {
        requestRepository = mock(AssetReturnRequestRepository.class);
        assignmentRepository = mock(TaiSanCapPhatRepository.class);
        userRepository = mock(AppUserRepository.class);
        controller = new PersonalReturnRequestController(requestRepository, assignmentRepository,
                userRepository, mock(ActivityLogService.class));
        authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("employee");
        assignment = new TaiSanCapPhat();
        assignment.setUsernameNguoiNhan("employee");
        assignment.setNguoiNhan("Employee Name");
        TaiSan asset = new TaiSan();
        asset.setTaiSanId(15);
        asset.setMaTaiSan("TS-015");
        asset.setTenTaiSan("Laptop");
        assignment.setTaiSan(asset);
        when(assignmentRepository.findById(7)).thenReturn(Optional.of(assignment));
        AppUser user = new AppUser();
        user.setUsername("employee");
        when(userRepository.findByUsernameIgnoreCase("employee")).thenReturn(Optional.of(user));
        when(requestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsReturnRequestOnlyForAnAssignmentOwnedByTheCurrentUser() {
        when(requestRepository.existsByAssignment_IdAndRequesterIgnoreCaseAndStatus(null, "employee", "PENDING"))
                .thenReturn(false);

        var response = controller.create(
                new PersonalReturnRequestController.CreateRequest(7, "Chuyển phòng ban"), authentication);

        assertEquals(15, response.taiSanId());
        assertEquals("TS-015", response.maTaiSan());
        assertEquals("Chuyển phòng ban", response.reason());
        assertEquals("PENDING", response.status());
        verify(requestRepository).save(any(AssetReturnRequest.class));
    }

    @Test
    void rejectsReturnRequestForAnotherEmployeesAsset() {
        assignment.setUsernameNguoiNhan("another-employee");

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> controller.create(
                        new PersonalReturnRequestController.CreateRequest(7, "Chuyển phòng"), authentication));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verify(requestRepository, never()).save(any());
    }

    @Test
    void preventsDuplicatePendingReturnRequests() {
        when(requestRepository.existsByAssignment_IdAndRequesterIgnoreCaseAndStatus(null, "employee", "PENDING"))
                .thenReturn(true);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> controller.create(
                        new PersonalReturnRequestController.CreateRequest(7, "Chuyển phòng"), authentication));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(requestRepository, never()).save(any());
    }
}
