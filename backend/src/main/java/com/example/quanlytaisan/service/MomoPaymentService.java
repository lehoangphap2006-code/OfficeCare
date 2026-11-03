package com.example.quanlytaisan.service;

import com.example.quanlytaisan.dto.ThuongMaiOrderResponse;
import com.example.quanlytaisan.entity.ThuongMaiOrder;
import com.example.quanlytaisan.repository.ThuongMaiOrderRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class MomoPaymentService {
    private static final Logger logger = LoggerFactory.getLogger(MomoPaymentService.class);
    private static final Duration CHECKOUT_LIFETIME = Duration.ofMinutes(15);
    private static final List<String> PAYABLE_ORDER_STATUSES =
            List.of("DA_DAT", "NHAN_MOT_PHAN", "DA_NHAN", "HOAN_TAT");

    private final ThuongMaiOrderRepository orderRepository;
    private final ActivityLogService activityLogService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final TransactionTemplate transactions;
    private final boolean enabled;
    private final String partnerCode;
    private final String accessKey;
    private final String secretKey;
    private final String endpoint;
    private final String redirectUrl;
    private final String ipnUrl;

    public MomoPaymentService(
            ThuongMaiOrderRepository orderRepository,
            ActivityLogService activityLogService,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager,
            @Value("${app.payment.momo.enabled:false}") boolean enabled,
            @Value("${app.payment.momo.partner-code:}") String partnerCode,
            @Value("${app.payment.momo.access-key:}") String accessKey,
            @Value("${app.payment.momo.secret-key:}") String secretKey,
            @Value("${app.payment.momo.endpoint}") String endpoint,
            @Value("${app.payment.momo.redirect-url}") String redirectUrl,
            @Value("${app.payment.momo.ipn-url:}") String ipnUrl) {
        this.orderRepository = orderRepository;
        this.activityLogService = activityLogService;
        this.objectMapper = objectMapper;
        this.transactions = new TransactionTemplate(transactionManager);
        this.enabled = enabled;
        this.partnerCode = partnerCode;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.endpoint = endpoint;
        this.redirectUrl = redirectUrl;
        this.ipnUrl = ipnUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public CheckoutResponse createCheckout(Integer orderId) {
        ensureConfigured();
        CheckoutAttempt attempt = transactions.execute(status -> prepareCheckout(orderId));
        if (attempt == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Không thể tạo yêu cầu thanh toán MoMo");
        }
        if (attempt.payUrl() != null) {
            return new CheckoutResponse(attempt.payUrl());
        }

        JsonNode response = sendCreateRequest(attempt);
        int resultCode = response.path("resultCode").asInt(-1);
        String payUrl = response.path("payUrl").asText("");
        if (resultCode != 0) {
            logger.warn("MoMo checkout rejected request {} with resultCode {}", attempt.requestId(), resultCode);
            transactions.executeWithoutResult(status -> orderRepository.findByIdForUpdate(orderId)
                    .filter(order -> attempt.requestId().equals(order.getMomoRequestId()))
                    .ifPresent(order -> {
                        order.setMomoRequestId(null);
                        order.setMomoOrderId(null);
                        order.setMomoPayUrl(null);
                        order.setMomoCheckoutCreatedAt(null);
                        orderRepository.save(order);
                    }));
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "MoMo chưa thể tạo phiên thanh toán");
        }
        if (payUrl.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "MoMo không trả về liên kết thanh toán");
        }
        validatePayUrl(payUrl);
        transactions.executeWithoutResult(status -> {
            ThuongMaiOrder order = orderRepository.findByIdForUpdate(orderId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng"));
            if (attempt.requestId().equals(order.getMomoRequestId())
                    && "CHUA_THANH_TOAN".equals(order.getTrangThaiThanhToan())) {
                order.setMomoPayUrl(payUrl);
                orderRepository.save(order);
            }
        });
        return new CheckoutResponse(payUrl);
    }

    public void processIpn(Map<String, ?> callback) {
        ensureConfigured();
        verifyCallbackSignature(callback);

        String callbackPartnerCode = value(callback, "partnerCode");
        String requestId = value(callback, "requestId");
        String momoOrderId = value(callback, "orderId");
        String extraData = value(callback, "extraData");
        String amountText = value(callback, "amount");
        String resultCodeText = value(callback, "resultCode");
        String transactionId = value(callback, "transId");

        if (!partnerCode.equals(callbackPartnerCode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MoMo partner không hợp lệ");
        }
        Integer orderId = decodeOrderId(extraData);
        transactions.executeWithoutResult(status -> {
            ThuongMaiOrder order = orderRepository.findByIdForUpdate(orderId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng"));
            if (!"MUA".equals(order.getLoaiDon())
                    || !"MOMO".equals(order.getPhuongThucThanhToan())
                    || !requestId.equals(order.getMomoRequestId())
                    || !momoOrderId.equals(order.getMomoOrderId())
                    || !amountMatches(order.getTongTien(), amountText)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Thông tin thanh toán MoMo không khớp đơn hàng");
            }

            if (!"0".equals(resultCodeText)) {
                order.setMomoRequestId(null);
                order.setMomoOrderId(null);
                order.setMomoPayUrl(null);
                order.setMomoCheckoutCreatedAt(null);
                orderRepository.save(order);
                return;
            }

            if ("DA_THANH_TOAN".equals(order.getTrangThaiThanhToan())) {
                if (transactionId.equals(order.getMaGiaoDich())) return;
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Đơn hàng đã được thanh toán bằng giao dịch khác");
            }
            if (!"CHUA_THANH_TOAN".equals(order.getTrangThaiThanhToan())
                    || transactionId.isBlank()
                    || transactionId.length() > 40
                    || orderRepository.existsByMaGiaoDichIgnoreCase(transactionId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Giao dịch MoMo không thể ghi nhận");
            }

            ThuongMaiOrderResponse previous = toResponse(order);
            order.setTrangThaiThanhToan("DA_THANH_TOAN");
            order.setMaGiaoDich(transactionId);
            ThuongMaiOrder saved = orderRepository.save(order);
            ThuongMaiOrderResponse updated = toResponse(saved);
            activityLogService.record("MOMO_CALLBACK", "PAY", "ThuongMaiOrder", saved.getId(),
                    previous, updated, null);
        });
    }

    private CheckoutAttempt prepareCheckout(Integer orderId) {
        ThuongMaiOrder order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng"));
        if (!"MUA".equals(order.getLoaiDon()) || !"MOMO".equals(order.getPhuongThucThanhToan())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Đơn hàng không sử dụng thanh toán MoMo");
        }
        if (!PAYABLE_ORDER_STATUSES.contains(order.getTrangThai())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Đơn hàng chưa ở trạng thái có thể thanh toán");
        }
        if (!"CHUA_THANH_TOAN".equals(order.getTrangThaiThanhToan())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Đơn hàng đã được thanh toán");
        }

        long amount = amountAsVnd(order.getTongTien());
        Instant now = Instant.now();
        if (order.getMomoRequestId() != null
                && order.getMomoOrderId() != null
                && order.getMomoCheckoutCreatedAt() != null
                && order.getMomoCheckoutCreatedAt().plus(CHECKOUT_LIFETIME).isAfter(now)) {
            return new CheckoutAttempt(order.getId(), order.getMomoRequestId(), order.getMomoOrderId(),
                    amount, orderInfo(order), extraData(order), order.getMomoPayUrl());
        }

        String requestId = UUID.randomUUID().toString().replace("-", "");
        String momoOrderId = "OC" + order.getId() + "-" + UUID.randomUUID().toString().replace("-", "");
        order.setMomoRequestId(requestId);
        order.setMomoOrderId(momoOrderId);
        order.setMomoPayUrl(null);
        order.setMomoCheckoutCreatedAt(now);
        orderRepository.save(order);
        return new CheckoutAttempt(order.getId(), requestId, momoOrderId, amount,
                orderInfo(order), extraData(order), null);
    }

    private JsonNode sendCreateRequest(CheckoutAttempt attempt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("partnerCode", partnerCode);
        payload.put("requestId", attempt.requestId());
        payload.put("amount", attempt.amount());
        payload.put("orderId", attempt.momoOrderId());
        payload.put("orderInfo", attempt.orderInfo());
        payload.put("redirectUrl", redirectUrl);
        payload.put("ipnUrl", ipnUrl);
        payload.put("lang", "vi");
        payload.put("requestType", "captureWallet");
        payload.put("extraData", attempt.extraData());
        payload.put("signature", sign(createSignatureData(attempt)));

        try {
            HttpRequest request = HttpRequest.newBuilder(requireHttpsUri(endpoint, "MOMO_ENDPOINT"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> result = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (result.statusCode() < 200 || result.statusCode() >= 300) {
                logger.warn("MoMo checkout returned HTTP {}", result.statusCode());
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Không kết nối được MoMo");
            }
            JsonNode body = objectMapper.readTree(result.body());
            if (body == null || !body.isObject()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Phản hồi MoMo không hợp lệ");
            }
            return body;
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Yêu cầu MoMo bị gián đoạn", exception);
        } catch (IOException exception) {
            logger.error("MoMo checkout request failed", exception);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Không thể kết nối MoMo", exception);
        }
    }

    private void verifyCallbackSignature(Map<String, ?> callback) {
        String provided = value(callback, "signature");
        if (provided.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Thiếu chữ ký MoMo");
        }
        String expected = sign(callbackSignatureData(callback));
        if (!MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                provided.getBytes(StandardCharsets.US_ASCII))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Chữ ký MoMo không hợp lệ");
        }
    }

    private String createSignatureData(CheckoutAttempt attempt) {
        return "accessKey=" + accessKey
                + "&amount=" + attempt.amount()
                + "&extraData=" + attempt.extraData()
                + "&ipnUrl=" + ipnUrl
                + "&orderId=" + attempt.momoOrderId()
                + "&orderInfo=" + attempt.orderInfo()
                + "&partnerCode=" + partnerCode
                + "&redirectUrl=" + redirectUrl
                + "&requestId=" + attempt.requestId()
                + "&requestType=captureWallet";
    }

    private String callbackSignatureData(Map<String, ?> callback) {
        return "accessKey=" + accessKey
                + "&amount=" + value(callback, "amount")
                + "&extraData=" + value(callback, "extraData")
                + "&message=" + value(callback, "message")
                + "&orderId=" + value(callback, "orderId")
                + "&orderInfo=" + value(callback, "orderInfo")
                + "&orderType=" + value(callback, "orderType")
                + "&partnerCode=" + value(callback, "partnerCode")
                + "&payType=" + value(callback, "payType")
                + "&requestId=" + value(callback, "requestId")
                + "&responseTime=" + value(callback, "responseTime")
                + "&resultCode=" + value(callback, "resultCode")
                + "&transId=" + value(callback, "transId");
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Không thể ký yêu cầu thanh toán MoMo", exception);
        }
    }

    private void ensureConfigured() {
        if (!enabled) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Thanh toán MoMo chưa được bật");
        }
        if (partnerCode.isBlank() || accessKey.isBlank() || secretKey.isBlank()
                || redirectUrl.isBlank() || ipnUrl.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Thiếu cấu hình merchant MoMo hoặc URL nhận callback");
        }
        requireHttpsUri(endpoint, "MOMO_ENDPOINT");
        requireHttpsOrLocalUri(redirectUrl, "MOMO_REDIRECT_URL");
        requireHttpsUri(ipnUrl, "MOMO_IPN_URL");
    }

    private URI requireHttpsUri(String value, String propertyName) {
        URI uri = parseUri(value, propertyName);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    propertyName + " phải là URL HTTPS hợp lệ");
        }
        return uri;
    }

    private void requireHttpsOrLocalUri(String value, String propertyName) {
        URI uri = parseUri(value, propertyName);
        boolean localHttp = "http".equalsIgnoreCase(uri.getScheme())
                && ("localhost".equalsIgnoreCase(uri.getHost()) || "127.0.0.1".equals(uri.getHost()));
        if (uri.getHost() == null || (!localHttp && !"https".equalsIgnoreCase(uri.getScheme()))) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    propertyName + " phải là URL HTTPS (HTTP chỉ được phép trên localhost)");
        }
    }

    private URI parseUri(String value, String propertyName) {
        try {
            return URI.create(value);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    propertyName + " không hợp lệ", exception);
        }
    }

    private void validatePayUrl(String value) {
        URI uri = parseUri(value, "payUrl MoMo");
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null
                || !(host.equalsIgnoreCase("payment.momo.vn")
                || host.equalsIgnoreCase("test-payment.momo.vn"))) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "MoMo trả về liên kết thanh toán không hợp lệ");
        }
    }

    private long amountAsVnd(BigDecimal amount) {
        try {
            long value = amount.longValueExact();
            if (value <= 0) throw new ArithmeticException("amount must be positive");
            return value;
        } catch (ArithmeticException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Số tiền thanh toán MoMo phải là số nguyên VNĐ dương", exception);
        }
    }

    private boolean amountMatches(BigDecimal expected, String actual) {
        try {
            return amountAsVnd(expected) == Long.parseLong(actual);
        } catch (NumberFormatException | ResponseStatusException exception) {
            return false;
        }
    }

    private String orderInfo(ThuongMaiOrder order) {
        return "Thanh toan don mua " + order.getMaDonHang();
    }

    private String extraData(ThuongMaiOrder order) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("OFFICECARE:" + order.getId()).getBytes(StandardCharsets.UTF_8));
    }

    private Integer decodeOrderId(String extraData) {
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(extraData), StandardCharsets.UTF_8);
            if (!decoded.startsWith("OFFICECARE:")) throw new IllegalArgumentException();
            return Integer.valueOf(decoded.substring("OFFICECARE:".length()));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dữ liệu đơn hàng MoMo không hợp lệ", exception);
        }
    }

    private String value(Map<String, ?> values, String name) {
        Object value = values.get(name);
        return value == null ? "" : String.valueOf(value);
    }

    private ThuongMaiOrderResponse toResponse(ThuongMaiOrder order) {
        return new ThuongMaiOrderResponse(
                order.getId(), order.getMaDonHang(), order.getLoaiDon(), order.getTrangThai(),
                order.getPhuongThucThanhToan(), order.getTrangThaiThanhToan(), order.getTongTien(),
                order.getTaoLuc(), order.getMaGiaoDich(), order.getSupplierId(), order.getSupplierName(),
                order.getLyDoThanhLy(), order.getGhiChuThanhLy(), order.getPhuongThucNhanTien(),
                order.getNgayThanhLy(), order.getNgayDuKienGiao(), order.getNguoiYeuCau(), order.getNguoiDuyet(),
                order.getDuyetLuc(), order.getNhanHangLuc(), null, false, order.getGhiChuDuyetThanhLy(), List.of());
    }

    public record CheckoutResponse(String payUrl) {}

    private record CheckoutAttempt(
            Integer orderId,
            String requestId,
            String momoOrderId,
            long amount,
            String orderInfo,
            String extraData,
            String payUrl) {}
}
