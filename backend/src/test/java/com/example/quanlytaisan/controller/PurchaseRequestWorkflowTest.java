package com.example.quanlytaisan.controller;

import com.example.quanlytaisan.dto.PurchaseRequestDtos.CreateRequest;
import com.example.quanlytaisan.dto.PurchaseRequestDtos.PlaceOrderRequest;
import com.example.quanlytaisan.dto.ThuongMaiOrderRequest;
import com.example.quanlytaisan.dto.ThuongMaiOrderResponse;
import com.example.quanlytaisan.entity.LoaiTaiSan;
import com.example.quanlytaisan.entity.PurchaseRequest;
import com.example.quanlytaisan.entity.ThuongMaiOrder;
import com.example.quanlytaisan.repository.*;
import com.example.quanlytaisan.service.ActivityLogService;
import com.example.quanlytaisan.service.TaiSanVongDoiService;
import com.example.quanlytaisan.service.ThuongMaiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PurchaseRequestWorkflowTest {
    private static final String ORDER_REQUEST_ID = "c1fd7eca-f85d-4c7f-9cad-10fd5938fb7b";

    private PurchaseRequestRepository requestRepository;
    private LoaiTaiSanRepository categoryRepository;
    private ThuongMaiOrderRepository orderRepository;
    private ThuongMaiService commerceService;
    private ActivityLogService activityLog;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        requestRepository = mock(PurchaseRequestRepository.class);
        categoryRepository = mock(LoaiTaiSanRepository.class);
        orderRepository = mock(ThuongMaiOrderRepository.class);
        commerceService = mock(ThuongMaiService.class);
        activityLog = mock(ActivityLogService.class);
        authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("admin");
    }

    @Test
    void rejectsPurchaseRequestWithoutPositiveEstimate() {
        PersonalWorkspaceController controller = new PersonalWorkspaceController(
                mock(TaiSanCapPhatRepository.class), mock(AppUserRepository.class),
                mock(YeuCauBaoTriRepository.class), categoryRepository, requestRepository,
                mock(TaiSanVongDoiService.class), activityLog);
        when(categoryRepository.existsById(3)).thenReturn(true);

        assertThrows(ResponseStatusException.class,
                () -> controller.createPurchaseRequest(
                        new CreateRequest("Laptop", 3, 1, "Nhu cầu công việc", BigDecimal.ZERO), authentication));
        verify(requestRepository, never()).save(any());
    }

    @Test
    void reportsPendingPurchaseRequestCount() {
        AdminPurchaseRequestController controller = new AdminPurchaseRequestController(
                requestRepository, categoryRepository, activityLog, commerceService, orderRepository);
        when(requestRepository.countByStatus("PENDING")).thenReturn(3L);

        assertEquals(3L, controller.pendingCount());
    }

    @Test
    void allowsAdminToApproveLegacyRequestWithLowEstimate() {
        AdminPurchaseRequestController controller = new AdminPurchaseRequestController(
                requestRepository, categoryRepository, activityLog, commerceService, orderRepository);
        PurchaseRequest request = new PurchaseRequest();
        request.setRequester("employee");
        request.setAssetName("Màn hình");
        request.setCategoryId(3);
        request.setQuantity(1);
        request.setReason("Nhu cầu công việc");
        request.setEstimatedAmount(new BigDecimal("12"));
        request.setStatus("PENDING");
        when(requestRepository.findByIdForUpdate(9)).thenReturn(Optional.of(request));
        when(requestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(categoryRepository.findById(3)).thenReturn(Optional.of(new LoaiTaiSan()));

        var response = controller.review(9,
                new com.example.quanlytaisan.dto.PurchaseRequestDtos.ReviewRequest("APPROVED", null),
                authentication);

        assertEquals("APPROVED", response.status());
        assertEquals(new BigDecimal("12"), response.estimatedAmount());
        verify(requestRepository).save(request);
    }

    @Test
    void rejectsUnrealisticallySmallPurchaseEstimate() {
        PersonalWorkspaceController controller = new PersonalWorkspaceController(
                mock(TaiSanCapPhatRepository.class), mock(AppUserRepository.class),
                mock(YeuCauBaoTriRepository.class), categoryRepository, requestRepository,
                mock(TaiSanVongDoiService.class), activityLog);
        when(categoryRepository.existsById(3)).thenReturn(true);

        assertThrows(ResponseStatusException.class,
                () -> controller.createPurchaseRequest(
                        new CreateRequest("Màn hình", 3, 1, "Nhu cầu công việc", new BigDecimal("5")), authentication));
        verify(requestRepository, never()).save(any());
    }

    @Test
    void savesValidEstimateOnEmployeePurchaseRequest() {
        PersonalWorkspaceController controller = new PersonalWorkspaceController(
                mock(TaiSanCapPhatRepository.class), mock(AppUserRepository.class),
                mock(YeuCauBaoTriRepository.class), categoryRepository, requestRepository,
                mock(TaiSanVongDoiService.class), activityLog);
        when(categoryRepository.existsById(3)).thenReturn(true);
        when(categoryRepository.findById(3)).thenReturn(Optional.of(new LoaiTaiSan()));
        when(requestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = controller.createPurchaseRequest(
                new CreateRequest("Laptop", 3, 2, "Nhu cầu công việc", new BigDecimal("1250.50")), authentication);

        assertEquals(new BigDecimal("1250.50"), response.estimatedAmount());
        assertNull(response.purchaseOrderId());
    }

    @Test
    void allowsEmployeeToCancelOwnPendingPurchaseRequestAndLogsIt() {
        PersonalWorkspaceController controller = new PersonalWorkspaceController(
                mock(TaiSanCapPhatRepository.class), mock(AppUserRepository.class),
                mock(YeuCauBaoTriRepository.class), categoryRepository, requestRepository,
                mock(TaiSanVongDoiService.class), activityLog);
        PurchaseRequest request = approvedRequest();
        request.setRequester("employee");
        request.setStatus("PENDING");
        when(requestRepository.findByIdForUpdate(8)).thenReturn(Optional.of(request));
        when(requestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(categoryRepository.findById(3)).thenReturn(Optional.of(new LoaiTaiSan()));

        var response = controller.cancelPurchaseRequest(8, authenticationFor("employee"));

        assertEquals("CANCELLED", response.status());
        verify(activityLog).record(eq("employee"), eq("CANCEL"), eq("PurchaseRequest"), eq(8),
                any(), any(), any());
    }

    @Test
    void preventsEmployeeFromCancellingAnotherUsersPurchaseRequest() {
        PersonalWorkspaceController controller = new PersonalWorkspaceController(
                mock(TaiSanCapPhatRepository.class), mock(AppUserRepository.class),
                mock(YeuCauBaoTriRepository.class), categoryRepository, requestRepository,
                mock(TaiSanVongDoiService.class), activityLog);
        PurchaseRequest request = approvedRequest();
        request.setRequester("another-employee");
        request.setStatus("PENDING");
        when(requestRepository.findByIdForUpdate(8)).thenReturn(Optional.of(request));

        assertThrows(ResponseStatusException.class,
                () -> controller.cancelPurchaseRequest(8, authenticationFor("employee")));
        verify(requestRepository, never()).save(any());
    }

    @Test
    void preventsCancellingPurchaseRequestAfterItWasApproved() {
        PersonalWorkspaceController controller = new PersonalWorkspaceController(
                mock(TaiSanCapPhatRepository.class), mock(AppUserRepository.class),
                mock(YeuCauBaoTriRepository.class), categoryRepository, requestRepository,
                mock(TaiSanVongDoiService.class), activityLog);
        PurchaseRequest request = approvedRequest();
        request.setRequester("employee");
        when(requestRepository.findByIdForUpdate(8)).thenReturn(Optional.of(request));

        assertThrows(ResponseStatusException.class,
                () -> controller.cancelPurchaseRequest(8, authenticationFor("employee")));
        verify(requestRepository, never()).save(any());
    }

    @Test
    void convertsApprovedRequestToLinkedOrderWithRequestedItemDetails() {
        AdminPurchaseRequestController controller = new AdminPurchaseRequestController(
                requestRepository, categoryRepository, activityLog, commerceService, orderRepository);
        PurchaseRequest purchaseRequest = approvedRequest();
        ThuongMaiOrder order = new ThuongMaiOrder();
        order.setId(17);
        order.setMaDonHang("DH-0000017");
        when(requestRepository.findByIdForUpdate(8)).thenReturn(Optional.of(purchaseRequest));
        when(requestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(categoryRepository.existsById(3)).thenReturn(true);
        when(orderRepository.findById(17)).thenReturn(Optional.of(order));
        when(commerceService.createOrder(any())).thenReturn(orderResponse(17, "DH-0000017"));

        var response = controller.placeOrder(8, new PlaceOrderRequest(4, LocalDate.now().plusDays(7),
                "TIEN_MAT", new BigDecimal("12000000"), 6, null, ORDER_REQUEST_ID), authentication);

        assertEquals(17, response.purchaseOrderId());
        assertEquals("DH-0000017", response.purchaseOrderNumber());
        assertEquals(ORDER_REQUEST_ID, purchaseRequest.getOrderRequestId());
        verify(commerceService).createOrder(argThat(orderRequest -> {
            ThuongMaiOrderRequest.Item item = orderRequest.chiTiet().get(0);
            return "MUA".equals(orderRequest.loaiDon())
                    && ORDER_REQUEST_ID.equals(orderRequest.requestId())
                    && item.loaiTaiSanId().equals(3)
                    && item.tenSanPham().equals("Laptop")
                    && item.soLuong() == 2
                    && item.donGia().equals(new BigDecimal("12000000"))
                    && item.viTriId().equals(6);
        }));
    }

    @Test
    void preventsConvertingSameRequestTwice() {
        AdminPurchaseRequestController controller = new AdminPurchaseRequestController(
                requestRepository, categoryRepository, activityLog, commerceService, orderRepository);
        PurchaseRequest purchaseRequest = approvedRequest();
        purchaseRequest.setPurchaseOrder(new ThuongMaiOrder());
        purchaseRequest.setOrderRequestId(ORDER_REQUEST_ID);
        when(requestRepository.findByIdForUpdate(8)).thenReturn(Optional.of(purchaseRequest));

        var response = controller.placeOrder(8, new PlaceOrderRequest(4, LocalDate.now().plusDays(7),
                "TIEN_MAT", new BigDecimal("12000000"), 6, null, ORDER_REQUEST_ID), authentication);
        assertEquals(purchaseRequest.getPurchaseOrder().getId(), response.purchaseOrderId());
        verifyNoInteractions(commerceService);
    }

    @Test
    void requiresAUuidRequestIdBeforeCreatingAnOrder() {
        AdminPurchaseRequestController controller = new AdminPurchaseRequestController(
                requestRepository, categoryRepository, activityLog, commerceService, orderRepository);

        assertThrows(ResponseStatusException.class,
                () -> controller.placeOrder(8, new PlaceOrderRequest(4, LocalDate.now().plusDays(7),
                        "TIEN_MAT", new BigDecimal("12000000"), 6, null, "not-a-uuid"), authentication));
        verifyNoInteractions(commerceService, requestRepository);
    }

    @Test
    void preventsOrderCreationWhenRequestCategoryWasDeleted() {
        AdminPurchaseRequestController controller = new AdminPurchaseRequestController(
                requestRepository, categoryRepository, activityLog, commerceService, orderRepository);
        when(requestRepository.findByIdForUpdate(8)).thenReturn(Optional.of(approvedRequest()));
        when(categoryRepository.existsById(3)).thenReturn(false);

        assertThrows(ResponseStatusException.class,
                () -> controller.placeOrder(8, new PlaceOrderRequest(4, LocalDate.now().plusDays(7),
                        "TIEN_MAT", new BigDecimal("12000000"), 6, null), authentication));
        verifyNoInteractions(commerceService);
    }

    @Test
    void preventsDeletingCategoryUsedByPurchaseRequest() {
        var assetRepository = mock(TaiSanRepository.class);
        var controller = new LoaiTaiSanController(
                categoryRepository, assetRepository, requestRepository, activityLog);
        when(categoryRepository.existsById(3)).thenReturn(true);
        when(categoryRepository.findById(3)).thenReturn(Optional.of(new LoaiTaiSan()));
        when(assetRepository.countByLoaiTaiSan_LoaiId(3)).thenReturn(0L);
        when(requestRepository.countByCategoryId(3)).thenReturn(1L);

        assertThrows(ResponseStatusException.class, () -> controller.xoa(3, null, authentication));
        verify(categoryRepository, never()).deleteById(3);
    }

    @Test
    void allowsCreatingPurchaseOrderWithTotalBelowOneThousandForLegacyRequests() {
        AdminPurchaseRequestController controller = new AdminPurchaseRequestController(
                requestRepository, categoryRepository, activityLog, commerceService, orderRepository);
        ThuongMaiOrder order = new ThuongMaiOrder();
        order.setId(17);
        order.setMaDonHang("DH-0000017");
        when(requestRepository.findByIdForUpdate(8)).thenReturn(Optional.of(approvedRequest()));
        when(requestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(categoryRepository.existsById(3)).thenReturn(true);
        when(orderRepository.findById(17)).thenReturn(Optional.of(order));
        when(commerceService.createOrder(any())).thenReturn(orderResponse(17, "DH-0000017"));

        controller.placeOrder(8, new PlaceOrderRequest(4, LocalDate.now().plusDays(7),
                "TIEN_MAT", new BigDecimal("12"), 6, null, ORDER_REQUEST_ID), authentication);

        verify(commerceService).createOrder(argThat(orderRequest ->
                orderRequest.chiTiet().get(0).donGia().equals(new BigDecimal("12"))));
    }

    private PurchaseRequest approvedRequest() {
        PurchaseRequest row = new PurchaseRequest();
        row.setRequester("employee");
        row.setAssetName("Laptop");
        row.setCategoryId(3);
        row.setQuantity(2);
        row.setReason("Nhu cầu công việc");
        row.setEstimatedAmount(new BigDecimal("25000000"));
        row.setStatus("APPROVED");
        return row;
    }

    private ThuongMaiOrderResponse orderResponse(Integer id, String number) {
        return new ThuongMaiOrderResponse(id, number, "MUA", "DA_DAT", "TIEN_MAT", "CHUA_THANH_TOAN",
                new BigDecimal("24000000"), Instant.now(), null, 4, "Supplier", null, null, null,
                null, LocalDate.now().plusDays(7), "admin", null, null, null, BigDecimal.ZERO, false, List.of());
    }

    private Authentication authenticationFor(String username) {
        Authentication result = mock(Authentication.class);
        when(result.getName()).thenReturn(username);
        return result;
    }
}
