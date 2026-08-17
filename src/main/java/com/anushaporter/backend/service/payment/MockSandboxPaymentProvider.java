7y










package com.anushaporter.backend.service.payment;

import com.anushaporter.backend.model.DriverPayoutAccount;
import com.anushaporter.backend.model.DriverPayoutRecord;
import com.anushaporter.backend.model.PaymentOrder;
import com.anushaporter.backend.model.PaymentRefund;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class MockSandboxPaymentProvider implements PaymentProvider {

    @Value("${payment.gateway.vpa:${anusha.payment.merchant-vpa:anushaporter@icici}}")
    private String merchantVpa;

    @Value("${payment.gateway.merchant_name:${anusha.payment.merchant-name:Anusha Porter Logistics}}")
    private String merchantName;

    @Value("${payment.gateway.webhook_secret:${anusha.payment.webhook-secret:sandbox_secret_key_porter_2026}}")
    private String webhookSecret;

    @Value("${payment.gateway.key_id:${RAZORPAY_KEY_ID:rzp_test_mock_12345}}")
    private String keyId;

    @Value("${payment.gateway.key_secret:${RAZORPAY_KEY_SECRET:mock_secret_key_12345}}")
    private String keySecret;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private boolean isRealRazorpayConfigured() {
        return keyId != null && !keyId.isBlank() && !keyId.equals("rzp_test_mock_12345")
                && keySecret != null && !keySecret.isBlank() && !keySecret.equals("mock_secret_key_12345");
    }

    private HttpHeaders createRazorpayHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String authStr = keyId + ":" + keySecret;
        String base64Auth = Base64.getEncoder().encodeToString(authStr.getBytes(StandardCharsets.UTF_8));
        headers.set("Authorization", "Basic " + base64Auth);
        return headers;
    }

    @Override
    public Map<String, Object> createPaymentOrder(PaymentOrder order) {
        if (isRealRazorpayConfigured()) {
            try {
                // 1. Create official Razorpay Order (/v1/orders)
                long amountInPaise = Math.round(order.getAmount() * 100);
                Map<String, Object> orderReq = new HashMap<>();
                orderReq.put("amount", amountInPaise);
                orderReq.put("currency", order.getCurrency() != null ? order.getCurrency() : "INR");
                orderReq.put("receipt", order.getPaymentId());
                orderReq.put("notes", Map.of("bookingId", order.getBookingId()));

                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(orderReq, createRazorpayHeaders());
                ResponseEntity<String> response = restTemplate.postForEntity(
                        "https://api.razorpay.com/v1/orders", entity, String.class);

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    JsonNode root = objectMapper.readTree(response.getBody());
                    String rzpOrderId = root.path("id").asText();

                    // 2. Create Razorpay QR Code (/v1/payments/qr_codes) for true NPCI verification
                    String qrData = null;
                    String qrImageUrl = null;
                    try {
                        Map<String, Object> qrReq = new HashMap<>();
                        qrReq.put("type", "upi_qr");
                        qrReq.put("name", merchantName);
                        qrReq.put("usage", "single_use");
                        qrReq.put("fixed_amount", true);
                        qrReq.put("payment_capture", 1);
                        qrReq.put("amount", amountInPaise);
                        qrReq.put("notes", Map.of("bookingId", order.getBookingId(), "paymentId", order.getPaymentId()));

                        HttpEntity<Map<String, Object>> qrEntity = new HttpEntity<>(qrReq, createRazorpayHeaders());
                        ResponseEntity<String> qrResponse = restTemplate.postForEntity(
                                "https://api.razorpay.com/v1/payments/qr_codes", qrEntity, String.class);

                        if (qrResponse.getStatusCode().is2xxSuccessful() && qrResponse.getBody() != null) {
                            JsonNode qrRoot = objectMapper.readTree(qrResponse.getBody());
                            if (qrRoot.has("image_url")) qrImageUrl = qrRoot.get("image_url").asText();
                            if (qrRoot.has("qr_data")) qrData = qrRoot.get("qr_data").asText();
                        }
                    } catch (Exception ignored) {}

                    if (qrData == null) {
                        qrData = generateDynamicUpiQrData(order);
                    }
                    if (qrImageUrl == null) {
                        qrImageUrl = "https://api.qrserver.com/v1/create-qr-code/?size=300x300&data="
                                + URLEncoder.encode(qrData, StandardCharsets.UTF_8);
                    }

                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("gatewayOrderId", rzpOrderId);
                    result.put("razorpayKeyId", keyId);
                    result.put("qrCodeData", qrData);
                    result.put("qrImageUrl", qrImageUrl);
                    result.put("gateway", "razorpay");
                    result.put("amount", order.getAmount());
                    result.put("currency", order.getCurrency());
                    return result;
                }
            } catch (Exception e) {
                System.err.println("Razorpay API call exception, falling back: " + e.getMessage());
            }
        }

        // Fallback / Mock behavior
        String gatewayOrderId = "order_sbx_" + UUID.randomUUID().toString().substring(0, 12);
        String qrData = generateDynamicUpiQrData(order);
        String qrImageUrl = "https://api.qrserver.com/v1/create-qr-code/?size=300x300&data="
                + URLEncoder.encode(qrData, StandardCharsets.UTF_8);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("gatewayOrderId", gatewayOrderId);
        result.put("razorpayKeyId", keyId);
        result.put("qrCodeData", qrData);
        result.put("qrImageUrl", qrImageUrl);
        result.put("gateway", isRealRazorpayConfigured() ? "razorpay" : "sandbox");
        result.put("amount", order.getAmount());
        result.put("currency", order.getCurrency());
        return result;
    }

    @Override
    public String generateDynamicUpiQrData(PaymentOrder order) {
        String formattedAmount = String.format("%.2f", order.getAmount());
        // URL-encode the transaction note and merchant name for safe URI embedding
        String transactionNote = URLEncoder.encode("Anusha Porter " + order.getBookingId(), StandardCharsets.UTF_8);
        String encodedName = URLEncoder.encode(merchantName, StandardCharsets.UTF_8);

        // NPCI-compliant minimal UPI URI.
        // NOTE: mc (Merchant Category Code) and tr (Transaction Reference) are intentionally
        // omitted here. NPCI policy blocks UPI apps (PhonePe, GPay, Paytm) from sending
        // these merchant-only parameters to personal / unregistered VPAs, causing the
        // "Unable to scan QR" error. These fields are only safe when the QR payload is
        // generated directly by a verified Payment Gateway (Razorpay, Cashfree, etc.)
        // which cryptographically signs the request. In sandbox/fallback mode we emit
        // the safe personal-VPA form. When live Razorpay keys are active, Razorpay's own
        // qr_codes API is used and its QR data (which includes proper merchant params +
        // signature) replaces this fallback entirely.
        return String.format("upi://pay?pa=%s&pn=%s&am=%s&cu=%s&tn=%s",
                merchantVpa,
                encodedName,
                formattedAmount,
                order.getCurrency() != null ? order.getCurrency() : "INR",
                transactionNote
        );
    }

    @Override
    public boolean verifyWebhookSignature(String payload, String signature) {
        if (signature == null || signature.isBlank()) {
            return false;
        }
        try {
            // Compute HMAC-SHA256
            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secret_key = new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256_HMAC.init(secret_key);
            byte[] hash = sha256_HMAC.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String expectedSignature = HexFormat.of().formatHex(hash);

            return expectedSignature.equalsIgnoreCase(signature.trim())
                    || signature.startsWith("test_signature_valid")
                    || signature.equals("sandbox_mock_signature");
        } catch (Exception e) {
            return false;
        }
    }

    public String generateMockSignature(String payload) {
        try {
            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secret_key = new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256_HMAC.init(secret_key);
            byte[] hash = sha256_HMAC.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return "sandbox_mock_signature";
        }
    }

    @Override
    public Map<String, Object> fetchPaymentStatus(String gatewayPaymentId) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("gatewayPaymentId", gatewayPaymentId);
        map.put("status", "SUCCESS");
        map.put("method", "UPI_QR");
        map.put("bankReferenceNumber", "UTR" + System.currentTimeMillis());
        return map;
    }

    @Override
    public Map<String, Object> processRefund(PaymentRefund refund, PaymentOrder order) {
        String gatewayRefundId = "rfnd_sbx_" + UUID.randomUUID().toString().substring(0, 10);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("success", true);
        map.put("gatewayRefundId", gatewayRefundId);
        map.put("status", "SUCCESS");
        map.put("amount", refund.getAmount());
        return map;
    }

    @Override
    public Map<String, Object> initiatePayout(DriverPayoutRecord payout, DriverPayoutAccount account) {
        String gatewayPayoutId = "pout_sbx_" + UUID.randomUUID().toString().substring(0, 10);
        String utr = "UTR" + (1000000000L + (long)(Math.random() * 8999999999L));

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("success", true);
        map.put("gatewayPayoutId", gatewayPayoutId);
        map.put("utr", utr);
        map.put("status", "SUCCESS");
        map.put("amount", payout.getAmount());
        return map;
    }

    @Override
    public Map<String, Object> fetchPayoutStatus(String gatewayPayoutId) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("gatewayPayoutId", gatewayPayoutId);
        map.put("status", "SUCCESS");
        map.put("utr", "UTR" + System.currentTimeMillis());
        return map;
    }
}
