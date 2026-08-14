package com.anushaporter.backend.service.payment;

import com.anushaporter.backend.model.DriverPayoutAccount;
import com.anushaporter.backend.model.DriverPayoutRecord;
import com.anushaporter.backend.model.PaymentOrder;
import com.anushaporter.backend.model.PaymentRefund;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class MockSandboxPaymentProvider implements PaymentProvider {

    @Value("${anusha.payment.merchant-vpa:anushaporter@icici}")
    private String merchantVpa;

    @Value("${anusha.payment.merchant-name:Anusha Porter Logistics}")
    private String merchantName;

    @Value("${anusha.payment.webhook-secret:sandbox_secret_key_porter_2026}")
    private String webhookSecret;

    @Override
    public Map<String, Object> createPaymentOrder(PaymentOrder order) {
        String gatewayOrderId = "order_sbx_" + UUID.randomUUID().toString().substring(0, 12);
        String qrData = generateDynamicUpiQrData(order);
        
        // Generate SVG or Data URI QR presentation
        String qrImageUrl = "https://api.qrserver.com/v1/create-qr-code/?size=300x300&data=" 
                + URLEncoder.encode(qrData, StandardCharsets.UTF_8);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("gatewayOrderId", gatewayOrderId);
        result.put("qrCodeData", qrData);
        result.put("qrImageUrl", qrImageUrl);
        result.put("gateway", "sandbox");
        result.put("amount", order.getAmount());
        result.put("currency", order.getCurrency());
        return result;
    }

    @Override
    public String generateDynamicUpiQrData(PaymentOrder order) {
        String formattedAmount = String.format("%.2f", order.getAmount());
        String transactionNote = URLEncoder.encode("Anusha Porter " + order.getBookingId(), StandardCharsets.UTF_8);
        String encodedName = URLEncoder.encode(merchantName, StandardCharsets.UTF_8);

        // Standard NPCI UPI URI Specification
        return String.format("upi://pay?pa=%s&pn=%s&mc=4214&tr=%s&tn=%s&am=%s&cu=%s",
                merchantVpa,
                encodedName,
                order.getPaymentId(),
                transactionNote,
                formattedAmount,
                order.getCurrency() != null ? order.getCurrency() : "INR"
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
