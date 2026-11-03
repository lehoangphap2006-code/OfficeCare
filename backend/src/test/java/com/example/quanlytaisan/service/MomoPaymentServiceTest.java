package com.example.quanlytaisan.service;

import com.example.quanlytaisan.entity.ThuongMaiOrder;
import com.example.quanlytaisan.repository.ThuongMaiOrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MomoPaymentServiceTest {
    private static final String SECRET = "sandbox-secret";
    private ThuongMaiOrderRepository orderRepository;
    private ActivityLogService activityLogService;
    private PlatformTransactionManager transactionManager;
    private MomoPaymentService service;
    private ThuongMaiOrder order;

    @BeforeEach
    void setUp() {
        orderRepository = mock(ThuongMaiOrderRepository.class);
        activityLogService = mock(ActivityLogService.class);
        transactionManager = mock(PlatformTransactionManager.class);
        TransactionStatus status = mock(TransactionStatus.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(status);
        doNothing().when(transactionManager).commit(status);
        doNothing().when(transactionManager).rollback(status);
        service = new MomoPaymentService(orderRepository, activityLogService, new ObjectMapper(),
                transactionManager, true, "PARTNER", "ACCESS", SECRET,
                "https://test-payment.momo.vn/v2/gateway/api/create",
                "http://localhost:5500/admin/procurement?momo_return=1",
                "https://officecare.example/api/payments/momo/ipn");

        order = new ThuongMaiOrder();
        order.setId(7);
        order.setMaDonHang("DH-0000007");
        order.setLoaiDon("MUA");
        order.setTrangThai("DA_DAT");
        order.setPhuongThucThanhToan("MOMO");
        order.setTrangThaiThanhToan("CHUA_THANH_TOAN");
        order.setTongTien(new BigDecimal("125000"));
        order.setMomoRequestId("request-123");
        order.setMomoOrderId("OC7-order-123");
        when(orderRepository.findByIdForUpdate(7)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void acceptsSignedSuccessfulCallbackAndRecordsPaymentOnce() {
        Map<String, Object> callback = validCallback();
        callback.put("signature", signCallback(callback));

        service.processIpn(callback);

        assertEquals("DA_THANH_TOAN", order.getTrangThaiThanhToan());
        assertEquals("987654321", order.getMaGiaoDich());
        verify(activityLogService).record(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsCallbackWithInvalidSignatureBeforeLookingUpOrder() {
        Map<String, Object> callback = validCallback();
        callback.put("signature", "invalid");

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.processIpn(callback));

        assertEquals(401, error.getStatusCode().value());
        verifyNoInteractions(orderRepository);
    }

    @Test
    void rejectsSignedCallbackWhenAmountDoesNotMatchOrder() {
        Map<String, Object> callback = validCallback();
        callback.put("amount", "125001");
        callback.put("signature", signCallback(callback));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.processIpn(callback));

        assertEquals(409, error.getStatusCode().value());
        assertEquals("CHUA_THANH_TOAN", order.getTrangThaiThanhToan());
    }

    @Test
    void acceptsDuplicateSuccessCallbackWithoutRecordingPaymentTwice() {
        Map<String, Object> callback = validCallback();
        callback.put("signature", signCallback(callback));

        service.processIpn(callback);
        service.processIpn(callback);

        verify(activityLogService).record(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void reusesAnUnexpiredCheckoutAttemptWhenItsPaymentUrlHasNotBeenSavedYet() {
        order.setMomoCheckoutCreatedAt(Instant.now());

        Object attempt = ReflectionTestUtils.invokeMethod(service, "prepareCheckout", 7);

        assertEquals("request-123", ReflectionTestUtils.getField(attempt, "requestId"));
        assertEquals("OC7-order-123", ReflectionTestUtils.getField(attempt, "momoOrderId"));
    }

    @Test
    void signsRequestsWithHmacSha256Hex() {
        ReflectionTestUtils.setField(service, "secretKey", "key");

        String signature = ReflectionTestUtils.invokeMethod(service, "sign",
                "The quick brown fox jumps over the lazy dog");

        assertEquals("f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8",
                signature);
    }

    private Map<String, Object> validCallback() {
        Map<String, Object> callback = new LinkedHashMap<>();
        callback.put("partnerCode", "PARTNER");
        callback.put("requestId", "request-123");
        callback.put("orderId", "OC7-order-123");
        callback.put("amount", "125000");
        callback.put("orderInfo", "Thanh toan don mua DH-0000007");
        callback.put("orderType", "momo_wallet");
        callback.put("transId", "987654321");
        callback.put("resultCode", "0");
        callback.put("message", "Successful.");
        callback.put("payType", "qr");
        callback.put("responseTime", "1760000000000");
        callback.put("extraData", Base64.getUrlEncoder().withoutPadding()
                .encodeToString("OFFICECARE:7".getBytes(StandardCharsets.UTF_8)));
        return callback;
    }

    private String signCallback(Map<String, Object> callback) {
        String signatureData = ReflectionTestUtils.invokeMethod(service, "callbackSignatureData", callback);
        return ReflectionTestUtils.invokeMethod(service, "sign", signatureData);
    }
}
