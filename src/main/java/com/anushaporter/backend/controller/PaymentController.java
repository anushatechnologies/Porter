package com.anushaporter.backend.controller;

import com.anushaporter.backend.model.PaymentOrder;
import com.anushaporter.backend.model.PaymentRefund;
import com.anushaporter.backend.model.PaymentWebhookEvent;
import com.anushaporter.backend.repository.PaymentOrderRepository;
import com.anushaporter.backend.repository.PaymentWebhookEventRepository;
import com.anushaporter.backend.service.payment.MockSandboxPaymentProvider;
import com.anushaporter.backend.service.payment.PaymentLifecycleService;
import com.anushaporter.backend.service.payment.PaymentProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    @Autowired
    private PaymentLifecycleService paymentLifecycleService;

    @Autowired
    private PaymentOrderRepository paymentOrderRepository;

    @Autowired
    private PaymentWebhookEventRepository webhookEventRepository;

    @Autowired
    private PaymentProvider paymentProvider;

    /** Reflects the active payment gateway (razorpay, cashfree, sandbox, etc.) */
    @org.springframework.beans.factory.annotation.Value("${payment.gateway.provider:sandbox}")
    private String activeGatewayProvider;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private com.anushaporter.backend.service.DriverWalletService driverWalletService;

    @Autowired(required = false)
    private DriverAPIController driverAPIController;

    /**
     * POST /api/payments/create or POST /api/payments/initiate or POST /api/payments/razorpay/create-order
     * Generates a unique payment ID, invoice ID, and dynamic UPI QR code / Razorpay Order.
     */
    @PostMapping({"/create", "/initiate", "/create-order", "/razorpay/create-order", "/razorpay/create"})
    public ResponseEntity<?> createPayment(
            jakarta.servlet.http.HttpServletRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody Map<String, Object> payload
    ) {
        try {
            String bookingId = (String) payload.get("bookingId");
            Double amount = payload.get("amount") != null ? Double.parseDouble(String.valueOf(payload.get("amount"))) : null;
            String currency = payload.get("currency") != null ? String.valueOf(payload.get("currency")) : "INR";
            String paymentMethod = (String) payload.getOrDefault("paymentMethod", "RAZORPAY");

            // Check if this is a driver recharge request (starts with RECH_ or called via /razorpay/create-order with amount)
            boolean isRecharge = (bookingId != null && bookingId.startsWith("RECH_"))
                    || (bookingId == null && amount != null)
                    || "RECHARGE".equalsIgnoreCase(String.valueOf(payload.get("transactionType")));

            if (isRecharge) {
                if (bookingId == null || bookingId.isBlank()) {
                    bookingId = "RECH_" + System.currentTimeMillis();
                }
                double finalAmount = amount != null ? amount : 500.0;
                com.anushaporter.backend.model.Driver driver = driverAPIController != null ? driverAPIController.getAuthenticatedDriver(request) : null;
                String driverId = driver != null ? String.valueOf(driver.getId()) : (String) payload.get("driverId");

                PaymentOrder payment = paymentLifecycleService.createRazorpayOrder(bookingId, finalAmount, driverId, currency);

                Map<String, Object> response = new LinkedHashMap<>();
                response.put("success", true);
                response.put("razorpayOrderId", payment.getGatewayOrderId());
                response.put("keyId", paymentProvider.getKeyId());
                response.put("amount", payment.getAmount());
                response.put("currency", payment.getCurrency());
                response.put("paymentId", payment.getPaymentId());
                response.put("bookingId", payment.getBookingId());
                response.put("status", payment.getStatus().name());
                response.put("gateway", payment.getGateway());
                return ResponseEntity.ok(response);
            }

            if (bookingId == null || bookingId.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "bookingId is required"));
            }

            if (idempotencyKey == null) {
                idempotencyKey = (String) payload.get("idempotencyKey");
            }

            PaymentOrder payment = paymentLifecycleService.createOrFetchPaymentOrder(bookingId, paymentMethod, idempotencyKey);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("razorpayOrderId", payment.getGatewayOrderId());
            response.put("keyId", paymentProvider.getKeyId());
            response.put("paymentId", payment.getPaymentId());
            response.put("bookingId", payment.getBookingId());
            response.put("invoiceId", payment.getInvoiceId());
            response.put("amount", payment.getAmount());
            response.put("currency", payment.getCurrency());
            response.put("status", payment.getStatus().name());
            response.put("paymentMethod", payment.getPaymentMethod());
            response.put("qrCodeData", payment.getQrCodeData());
            response.put("qrImageUrl", payment.getQrImageUrl());
            response.put("qrExpiresAt", payment.getQrExpiresAt());
            response.put("gateway", payment.getGateway());
            response.put("gatewayOrderId", payment.getGatewayOrderId());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("success", false, "message", "Failed to create payment: " + e.getMessage()));
        }
    }

    /**
     * GET /api/payments/:id or GET /api/payments/:id/status
     * Returns the live server-side payment state.
     */
    @GetMapping({"/{id}", "/{id}/status"})
    public ResponseEntity<?> getPaymentStatus(@PathVariable String id) {
        Optional<PaymentOrder> paymentOpt = paymentOrderRepository.findByPaymentId(id);
        if (paymentOpt.isEmpty()) {
            paymentOpt = paymentOrderRepository.findByBookingId(id);
        }

        if (paymentOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "Payment not found"));
        }

        PaymentOrder payment = paymentOpt.get();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("paymentId", payment.getPaymentId());
        response.put("bookingId", payment.getBookingId());
        response.put("invoiceId", payment.getInvoiceId());
        response.put("amount", payment.getAmount());
        response.put("currency", payment.getCurrency());
        response.put("status", payment.getStatus().name());
        response.put("paymentMethod", payment.getPaymentMethod());
        response.put("transactionId", payment.getTransactionId());
        response.put("gatewayPaymentId", payment.getGatewayPaymentId());
        response.put("qrCodeData", payment.getQrCodeData());
        response.put("qrImageUrl", payment.getQrImageUrl());
        response.put("paidAt", payment.getPaidAt());
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/payments/verify or POST /api/payments/razorpay/verify
     * Verifies payment completion & credits Driver Wallet if recharge.
     */
    @PostMapping({"/verify", "/razorpay/verify"})
    public ResponseEntity<?> verifyPayment(
            jakarta.servlet.http.HttpServletRequest request,
            @RequestBody Map<String, Object> payload
    ) {
        try {
            String paymentId = (String) payload.get("paymentId");
            String bookingId = (String) payload.get("bookingId");
            String rzpPaymentId = (String) payload.getOrDefault("razorpay_payment_id", payload.get("payment_id"));
            String rzpOrderId = (String) payload.getOrDefault("razorpay_order_id", payload.get("order_id"));
            String rzpSignature = (String) payload.getOrDefault("razorpay_signature", payload.get("signature"));

            String gatewayPaymentId = rzpPaymentId != null ? rzpPaymentId : (String) payload.getOrDefault("gatewayPaymentId", "pay_sbx_" + UUID.randomUUID().toString().substring(0, 8));
            String transactionId = (String) payload.getOrDefault("transactionId", "TXN_" + UUID.randomUUID().toString().substring(0, 10));
            String paymentMethod = (String) payload.getOrDefault("paymentMethod", "RAZORPAY");

            // 1. Signature Verification
            if (rzpSignature != null && !rzpSignature.isBlank()) {
                boolean isValid = paymentProvider.verifyPaymentSignature(rzpOrderId, rzpPaymentId, rzpSignature);
                if (!isValid) {
                    return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Invalid payment signature"));
                }
            }

            // Find existing PaymentOrder if any
            Optional<PaymentOrder> paymentOpt = Optional.empty();
            if (paymentId != null) {
                paymentOpt = paymentOrderRepository.findByPaymentId(paymentId);
            }
            if (paymentOpt.isEmpty() && bookingId != null) {
                paymentOpt = paymentOrderRepository.findByBookingId(bookingId);
            }

            // Check if this is a Driver Wallet Recharge
            boolean isRecharge = (bookingId != null && bookingId.startsWith("RECH_"))
                    || (paymentOpt.isPresent() && paymentOpt.get().getBookingId() != null && paymentOpt.get().getBookingId().startsWith("RECH_"))
                    || Boolean.TRUE.equals(payload.get("isRecharge"))
                    || "RECHARGE".equalsIgnoreCase(String.valueOf(payload.get("transactionType")));

            com.anushaporter.backend.model.Driver authDriver = driverAPIController != null ? driverAPIController.getAuthenticatedDriver(request) : null;
            if (authDriver != null || isRecharge) {
                String driverId = authDriver != null ? String.valueOf(authDriver.getId()) : null;
                if (driverId == null && paymentOpt.isPresent()) {
                    driverId = paymentOpt.get().getDriverId();
                }
                if (driverId == null && payload.get("driverId") != null) {
                    driverId = String.valueOf(payload.get("driverId"));
                }

                if (driverId != null && !driverId.isBlank()) {
                    Double rechargeAmount = payload.get("amount") != null
                            ? Double.parseDouble(String.valueOf(payload.get("amount")))
                            : (paymentOpt.isPresent() ? paymentOpt.get().getAmount() : 500.0);

                    com.anushaporter.backend.model.DriverWallet updatedWallet =
                            driverWalletService.rechargeWallet(driverId, rechargeAmount, gatewayPaymentId, "Wallet Recharge (Razorpay)");

                    if (paymentOpt.isPresent()) {
                        PaymentOrder p = paymentOpt.get();
                        p.setStatus(com.anushaporter.backend.model.PaymentStatus.SUCCESS);
                        p.setGatewayPaymentId(gatewayPaymentId);
                        p.setTransactionId(transactionId);
                        p.setPaidAt(java.time.LocalDateTime.now());
                        paymentOrderRepository.save(p);
                    }

                    boolean isEligible = driverWalletService.isDriverEligibleForRides(driverId);
                    String eligibilityReason = driverWalletService.getEligibilityReason(driverId);

                    Map<String, Object> walletMap = new LinkedHashMap<>();
                    walletMap.put("availableBalance", updatedWallet.getAvailableBalance());
                    walletMap.put("totalEarned", updatedWallet.getTotalEarned());
                    walletMap.put("platformCommission", updatedWallet.getPlatformCommission());
                    walletMap.put("minRequiredBalance", driverWalletService.getMinRequiredBalance());
                    walletMap.put("isEligible", isEligible);
                    walletMap.put("eligibilityReason", eligibilityReason);

                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("success", true);
                    response.put("message", "Payment verified and wallet credited successfully");
                    response.put("wallet", walletMap);
                    response.put("updatedBalance", updatedWallet.getAvailableBalance());
                    if (paymentOpt.isPresent()) {
                        response.put("paymentId", paymentOpt.get().getPaymentId());
                        response.put("bookingId", paymentOpt.get().getBookingId());
                    }
                    return ResponseEntity.ok(response);
                }
            }

            // Customer Booking Payment Verification Fallback
            if (paymentId == null && bookingId != null) {
                PaymentOrder existing = paymentLifecycleService.createOrFetchPaymentOrder(bookingId, paymentMethod, null);
                paymentId = existing.getPaymentId();
            }

            if (paymentId == null) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "paymentId or bookingId is required"));
            }

            PaymentOrder payment = paymentLifecycleService.processPaymentSuccess(
                    paymentId,
                    gatewayPaymentId,
                    transactionId,
                    paymentMethod,
                    payload.toString()
            );

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("message", "Payment verified and processed successfully");
            response.put("paymentId", payment.getPaymentId());
            response.put("bookingId", payment.getBookingId());
            response.put("status", payment.getStatus().name());
            response.put("amount", payment.getAmount());
            response.put("transactionId", payment.getTransactionId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(400).body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * POST /api/payments/webhook
     * Idempotent webhook handler with cryptographic signature verification.
     */
    @PostMapping("/webhook")
    public ResponseEntity<?> handlePaymentWebhook(
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String rzpSignature,
            @RequestHeader(value = "X-Webhook-Signature", required = false) String customSignature,
            @RequestBody String rawPayload
    ) {
        String signature = rzpSignature != null ? rzpSignature : customSignature;
        boolean isValid = paymentProvider.verifyWebhookSignature(rawPayload, signature);
        if (!isValid) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "Invalid webhook signature"));
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> eventMap = objectMapper.readValue(rawPayload, Map.class);
            String eventId = String.valueOf(eventMap.getOrDefault("eventId", "evt_" + UUID.randomUUID().toString().substring(0, 10)));
            String eventType = String.valueOf(eventMap.getOrDefault("event", "payment.captured"));

            // Check if webhook event was already processed (Idempotency)
            if (webhookEventRepository.existsByEventId(eventId)) {
                return ResponseEntity.ok(Map.of("success", true, "message", "Duplicate webhook ignored (already processed)"));
            }

            PaymentWebhookEvent webhookEvent = new PaymentWebhookEvent();
            webhookEvent.setEventId(eventId);
            // Use the configured gateway provider instead of hardcoding "sandbox"
            webhookEvent.setGateway(activeGatewayProvider);
            webhookEvent.setEventType(eventType);
            webhookEvent.setPayloadRaw(rawPayload);
            webhookEvent.setSignature(signature);

            String paymentId = (String) eventMap.get("paymentId");
            String gatewayPaymentId = (String) eventMap.get("gatewayPaymentId");
            String transactionId = (String) eventMap.get("transactionId");
            String method = eventMap.get("method") != null ? String.valueOf(eventMap.get("method")) : "UPI_QR";

            if (paymentId != null) {
                paymentLifecycleService.processPaymentSuccess(paymentId, gatewayPaymentId, transactionId, method, rawPayload);
            }

            webhookEvent.setProcessed(true);
            webhookEvent.setProcessedAt(java.time.LocalDateTime.now());
            webhookEventRepository.save(webhookEvent);

            return ResponseEntity.ok(Map.of("success", true, "message", "Webhook processed successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(400).body(Map.of("success", false, "message", "Webhook processing failed: " + e.getMessage()));
        }
    }

    /**
     * POST /api/payments/:id/refund
     * Full or partial refund processing.
     */
    @PostMapping("/{id}/refund")
    public ResponseEntity<?> refundPayment(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> payload
    ) {
        try {
            Double amount = payload != null && payload.get("amount") != null ? ((Number) payload.get("amount")).doubleValue() : null;
            String reason = payload != null ? (String) payload.get("reason") : "Customer cancellation";

            PaymentRefund refund = paymentLifecycleService.processRefund(id, amount, reason);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "refundId", refund.getRefundId(),
                    "amount", refund.getAmount(),
                    "status", refund.getStatus(),
                    "refundType", refund.getRefundType()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(400).body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * GET /api/payments/:id/receipt or GET /api/bookings/:id/invoice
     * Generates a digital receipt / invoice with branding and fare breakdown.
     */
    @GetMapping({"/{id}/receipt", "/{id}/invoice"})
    public ResponseEntity<?> getReceipt(@PathVariable String id) {
        Optional<PaymentOrder> paymentOpt = paymentOrderRepository.findByPaymentId(id);
        if (paymentOpt.isEmpty()) {
            paymentOpt = paymentOrderRepository.findByBookingId(id);
        }

        if (paymentOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "Payment invoice not found"));
        }

        PaymentOrder payment = paymentOpt.get();
        Map<String, Object> invoice = new LinkedHashMap<>();
        invoice.put("company", "Anusha Porter Logistics Private Limited");
        invoice.put("invoiceId", payment.getInvoiceId());
        invoice.put("paymentId", payment.getPaymentId());
        invoice.put("bookingId", payment.getBookingId());
        invoice.put("transactionId", payment.getTransactionId());
        invoice.put("amount", payment.getAmount());
        invoice.put("currency", payment.getCurrency());
        invoice.put("status", payment.getStatus().name());
        invoice.put("paymentMethod", payment.getPaymentMethod());
        invoice.put("customerName", payment.getCustomerName());
        invoice.put("customerPhone", payment.getCustomerPhone());
        invoice.put("driverName", payment.getDriverName());
        invoice.put("paidAt", payment.getPaidAt() != null ? payment.getPaidAt().toString() : null);
        invoice.put("fareBreakdown", payment.getFareBreakdownJson());
        return ResponseEntity.ok(invoice);
    }
}
