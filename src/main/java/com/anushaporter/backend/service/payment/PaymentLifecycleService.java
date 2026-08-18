package com.anushaporter.backend.service.payment;

import com.anushaporter.backend.model.*;
import com.anushaporter.backend.repository.*;
import com.anushaporter.backend.service.PushNotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class PaymentLifecycleService {

    @Autowired
    private PaymentOrderRepository paymentOrderRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private DriverEarningsRepository driverEarningsRepository;

    @Autowired
    private FinancialLedgerService ledgerService;

    @Autowired
    private CommissionService commissionService;

    @Autowired
    private PaymentProvider paymentProvider;

    @Autowired
    private PaymentWebhookEventRepository webhookEventRepository;

    @Autowired
    private PaymentRefundRepository refundRepository;

    @Autowired
    private PushNotificationService pushNotificationService;

    /**
     * Step 1: Create or fetch payment order with server-calculated fare and dynamic UPI QR.
     */
    @Transactional
    public PaymentOrder createOrFetchPaymentOrder(String bookingId, String paymentMethod, String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<PaymentOrder> existingByIdempotency = paymentOrderRepository.findByIdempotencyKey(idempotencyKey);
            if (existingByIdempotency.isPresent()) {
                return existingByIdempotency.get();
            }
        }

        Optional<PaymentOrder> existingOpt = paymentOrderRepository.findByBookingId(bookingId);
        if (existingOpt.isPresent()) {
            PaymentOrder existing = existingOpt.get();
            if (existing.getStatus() == PaymentStatus.PENDING || existing.getStatus() == PaymentStatus.CREATED) {
                if (paymentMethod != null && !paymentMethod.isBlank()) {
                    existing.setPaymentMethod(paymentMethod);
                }
                // Refresh QR expiration if expired
                if (existing.getQrExpiresAt() == null || existing.getQrExpiresAt().isBefore(LocalDateTime.now())) {
                    existing.setQrExpiresAt(LocalDateTime.now().plusMinutes(15));
                }
                return paymentOrderRepository.save(existing);
            }
            return existing;
        }

        Order order = orderRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + bookingId));

        // Server-Side Fare Calculation Verification
        double finalFare = order.getAmount() != null && order.getAmount() > 0 ? order.getAmount() : 150.0;
        double baseFare = order.getBaseFare() != null ? order.getBaseFare() : 49.0;
        double distanceFare = order.getDistanceFare() != null ? order.getDistanceFare() : (finalFare - baseFare);
        double helperCharges = order.getHelperCharges() != null ? order.getHelperCharges() : 0.0;
        double gstAmount = order.getGstAmount() != null ? order.getGstAmount() : 0.0;

        String fareJson = String.format(
                "{\"baseFare\":%.2f,\"distanceFare\":%.2f,\"helperCharges\":%.2f,\"gstAmount\":%.2f,\"finalFare\":%.2f}",
                baseFare, distanceFare, helperCharges, gstAmount, finalFare
        );

        String paymentId = "PAY_" + bookingId.replace("-", "_") + "_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String invoiceId = "INV-" + LocalDateTime.now().getYear() + "-" + String.format("%06d", (int)(Math.random() * 900000) + 100000);

        PaymentOrder paymentOrder = new PaymentOrder();
        paymentOrder.setPaymentId(paymentId);
        paymentOrder.setBookingId(bookingId);
        paymentOrder.setInvoiceId(invoiceId);
        paymentOrder.setCustomerId(order.getUserEmail());
        paymentOrder.setCustomerEmail(order.getUserEmail());
        paymentOrder.setCustomerName(order.getReceiverName() != null ? order.getReceiverName() : "Customer");
        paymentOrder.setCustomerPhone(order.getReceiverPhone());
        paymentOrder.setDriverId(order.getDriverId());
        paymentOrder.setDriverName(order.getDriverName());
        paymentOrder.setDriverPhone(order.getDriverPhone());
        paymentOrder.setAmount(finalFare);
        paymentOrder.setCurrency(order.getCurrency() != null ? order.getCurrency() : "INR");
        paymentOrder.setStatus(PaymentStatus.PENDING);
        paymentOrder.setPaymentMethod(paymentMethod != null ? paymentMethod : "UPI_QR");
        paymentOrder.setIdempotencyKey(idempotencyKey != null ? idempotencyKey : paymentId);
        paymentOrder.setFareBreakdownJson(fareJson);
        paymentOrder.setQrExpiresAt(LocalDateTime.now().plusMinutes(15));

        // Generate Gateway Order & Dynamic QR Code Payload.
        // The provider sets "gateway" to "razorpay" when live keys are active,
        // or "sandbox" when falling back to the mock provider.
        Map<String, Object> gatewayRes = paymentProvider.createPaymentOrder(paymentOrder);
        paymentOrder.setGatewayOrderId((String) gatewayRes.get("gatewayOrderId"));
        paymentOrder.setQrCodeData((String) gatewayRes.get("qrCodeData"));
        paymentOrder.setQrImageUrl((String) gatewayRes.get("qrImageUrl"));
        // Derive the gateway label from the provider result instead of hardcoding "sandbox"
        String resolvedGateway = gatewayRes.containsKey("gateway")
                ? String.valueOf(gatewayRes.get("gateway")) : "sandbox";
        paymentOrder.setGateway(resolvedGateway);

        return paymentOrderRepository.save(paymentOrder);
    }

    /**
     * Creates a dedicated Razorpay Order (e.g. for Driver Wallet Recharge)
     */
    @Transactional
    public PaymentOrder createRazorpayOrder(String bookingId, double amount, String driverId, String currency) {
        if (bookingId == null || bookingId.isBlank()) {
            bookingId = "RECH_" + System.currentTimeMillis();
        }
        if (currency == null || currency.isBlank()) {
            currency = "INR";
        }

        Optional<PaymentOrder> existingOpt = paymentOrderRepository.findByBookingId(bookingId);
        if (existingOpt.isPresent()) {
            PaymentOrder existing = existingOpt.get();
            if (existing.getStatus() == PaymentStatus.PENDING || existing.getStatus() == PaymentStatus.CREATED) {
                existing.setAmount(amount);
                existing.setCurrency(currency);
                return paymentOrderRepository.save(existing);
            }
            return existing;
        }

        String paymentId = "PAY_" + bookingId.replace("-", "_") + "_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String invoiceId = "INV-" + LocalDateTime.now().getYear() + "-" + String.format("%06d", (int)(Math.random() * 900000) + 100000);

        PaymentOrder paymentOrder = new PaymentOrder();
        paymentOrder.setPaymentId(paymentId);
        paymentOrder.setBookingId(bookingId);
        paymentOrder.setInvoiceId(invoiceId);
        paymentOrder.setDriverId(driverId);
        paymentOrder.setAmount(amount);
        paymentOrder.setCurrency(currency);
        paymentOrder.setStatus(PaymentStatus.CREATED);
        paymentOrder.setPaymentMethod("RAZORPAY");
        paymentOrder.setGateway("razorpay");
        paymentOrder.setFareBreakdownJson(String.format("{\"type\":\"WALLET_RECHARGE\",\"amount\":%.2f}", amount));
        paymentOrder.setQrExpiresAt(LocalDateTime.now().plusMinutes(30));

        Map<String, Object> gatewayRes = paymentProvider.createPaymentOrder(paymentOrder);
        if (gatewayRes != null) {
            if (gatewayRes.containsKey("gatewayOrderId")) {
                paymentOrder.setGatewayOrderId((String) gatewayRes.get("gatewayOrderId"));
            }
            if (gatewayRes.containsKey("qrCodeData")) {
                paymentOrder.setQrCodeData((String) gatewayRes.get("qrCodeData"));
            }
            if (gatewayRes.containsKey("qrImageUrl")) {
                paymentOrder.setQrImageUrl((String) gatewayRes.get("qrImageUrl"));
            }
            if (gatewayRes.containsKey("gateway")) {
                paymentOrder.setGateway((String) gatewayRes.get("gateway"));
            }
        }

        if (paymentOrder.getGatewayOrderId() == null) {
            paymentOrder.setGatewayOrderId("order_" + UUID.randomUUID().toString().substring(0, 14).replace("-", ""));
        }

        return paymentOrderRepository.save(paymentOrder);
    }

    /**
     * Step 2: Process verified payment success (Atomically updates payment, ledger, commission & driver earnings).
     */
    @Transactional
    public PaymentOrder processPaymentSuccess(
            String paymentId,
            String gatewayPaymentId,
            String transactionId,
            String paymentMethod,
            String rawPayload
    ) {
        PaymentOrder payment = paymentOrderRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));

        // Idempotency: If already SUCCESS, return existing record
        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            return payment;
        }

        if (!payment.getStatus().canTransitionTo(PaymentStatus.SUCCESS)) {
            throw new IllegalStateException("Cannot transition payment from " + payment.getStatus() + " to SUCCESS");
        }

        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setGatewayPaymentId(gatewayPaymentId);
        payment.setTransactionId(transactionId != null ? transactionId : "TXN_" + UUID.randomUUID().toString().substring(0, 10).toUpperCase());
        if (paymentMethod != null) payment.setPaymentMethod(paymentMethod);
        payment.setPaidAt(LocalDateTime.now());
        PaymentOrder savedPayment = paymentOrderRepository.save(payment);

        // 1. Record Gross Payment Received in Double-Entry Ledger
        ledgerService.recordEntry(
                payment.getBookingId(),
                payment.getPaymentId(),
                payment.getTransactionId(),
                payment.getDriverId(),
                payment.getCustomerId(),
                LedgerType.PAYMENT_RECEIVED,
                "CREDIT",
                payment.getAmount(),
                payment.getAmount(),
                "Gross customer payment received for " + payment.getBookingId(),
                payment.getGatewayPaymentId(),
                "GATEWAY_WEBHOOK"
        );

        // 2. Calculate Commission & Net Driver Pay
        Optional<Order> orderOpt = orderRepository.findByBookingId(payment.getBookingId());
        String serviceCategory = orderOpt.map(Order::getServiceName).orElse("vehicle");
        Map<String, Object> commCalc = commissionService.calculateCommission(payment.getAmount(), serviceCategory);
        double platformCommission = (Double) commCalc.get("platformCommission");
        double driverNetEarning = (Double) commCalc.get("driverNetEarning");

        // 3. Record Platform Commission in Ledger
        ledgerService.recordEntry(
                payment.getBookingId(),
                payment.getPaymentId(),
                payment.getTransactionId(),
                payment.getDriverId(),
                payment.getCustomerId(),
                LedgerType.PLATFORM_COMMISSION,
                "CREDIT",
                platformCommission,
                platformCommission,
                "Platform fee deduction (10%) for " + payment.getBookingId(),
                payment.getGatewayPaymentId(),
                "SYSTEM"
        );

        // 4. Record Driver Earning in Ledger
        ledgerService.recordEntry(
                payment.getBookingId(),
                payment.getPaymentId(),
                payment.getTransactionId(),
                payment.getDriverId(),
                payment.getCustomerId(),
                LedgerType.DRIVER_EARNING,
                "CREDIT",
                driverNetEarning,
                driverNetEarning,
                "Net payable earning credited to driver for " + payment.getBookingId(),
                payment.getGatewayPaymentId(),
                "SYSTEM"
        );

        // 5. Create Driver Earnings Entity
        DriverEarnings earnings = driverEarningsRepository.findByBookingId(payment.getBookingId())
                .orElse(new DriverEarnings());
        earnings.setDriverId(payment.getDriverId() != null ? payment.getDriverId() : "UNASSIGNED");
        earnings.setBookingId(payment.getBookingId());
        earnings.setPaymentId(payment.getPaymentId());
        earnings.setGrossFare(payment.getAmount());
        earnings.setPlatformCommission(platformCommission);
        earnings.setDriverNetEarning(driverNetEarning);
        earnings.setPaymentStatus("PAID");
        earnings.setSettlementStatus("PENDING");
        earnings.setRideCompletedAt(LocalDateTime.now());
        driverEarningsRepository.save(earnings);

        // 6. Update Driver App Balance
        if (payment.getDriverId() != null && !payment.getDriverId().isBlank()) {
            try {
                Long driverNumericId = Long.valueOf(payment.getDriverId());
                driverRepository.findById(driverNumericId).ifPresent(driver -> {
                    AppUser user = appUserRepository.findFirstByPhoneOrderByIdDesc(driver.getPhone()).orElse(null);
                    if (user != null) {
                        double currentBal = user.getWalletBalance() != null ? user.getWalletBalance() : 0.0;
                        user.setWalletBalance(currentBal + driverNetEarning);
                        appUserRepository.save(user);
                    }
                });
            } catch (NumberFormatException ignored) {}
        }

        // 7. Update Order Payment Status
        if (orderOpt.isPresent()) {
            Order order = orderOpt.get();
            order.setPaymentStatus("paid");
            if (!"delivered".equalsIgnoreCase(order.getStatus()) && !"completed".equalsIgnoreCase(order.getStatus())) {
                order.setStatus("delivered");
            }
            orderRepository.save(order);
            pushNotificationService.notifyOrderStatus(order, "payment_received");
        }

        return savedPayment;
    }

    /**
     * Step 3: Process payment failure
     */
    @Transactional
    public PaymentOrder processPaymentFailure(String paymentId, String reason) {
        PaymentOrder payment = paymentOrderRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));

        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            throw new IllegalStateException("Cannot mark already successful payment as failed.");
        }

        payment.setStatus(PaymentStatus.FAILED);
        payment.setFailureReason(reason != null ? reason : "Payment transaction failed");
        return paymentOrderRepository.save(payment);
    }

    /**
     * Step 4: Process full or partial refund
     */
    @Transactional
    public PaymentRefund processRefund(String paymentId, Double refundAmount, String reason) {
        PaymentOrder payment = paymentOrderRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));

        if (payment.getStatus() != PaymentStatus.SUCCESS && payment.getStatus() != PaymentStatus.PARTIALLY_REFUNDED) {
            throw new IllegalStateException("Only successful payments can be refunded.");
        }

        double amountToRefund = refundAmount != null && refundAmount > 0 ? refundAmount : payment.getAmount();
        if (amountToRefund > payment.getAmount()) {
            throw new IllegalArgumentException("Refund amount cannot exceed payment amount.");
        }

        String refundId = "RFND_" + UUID.randomUUID().toString().substring(0, 10).toUpperCase();
        PaymentRefund refund = new PaymentRefund();
        refund.setRefundId(refundId);
        refund.setPaymentId(payment.getPaymentId());
        refund.setBookingId(payment.getBookingId());
        refund.setAmount(amountToRefund);
        refund.setReason(reason != null ? reason : "Customer refund request");
        refund.setRefundType(amountToRefund >= payment.getAmount() ? "FULL_REFUND" : "PARTIAL_REFUND");
        refund.setStatus("SUCCESS");

        Map<String, Object> gatewayRes = paymentProvider.processRefund(refund, payment);
        refund.setGatewayRefundId((String) gatewayRes.get("gatewayRefundId"));
        PaymentRefund savedRefund = refundRepository.save(refund);

        // Update Payment Status
        payment.setStatus(amountToRefund >= payment.getAmount() ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED);
        paymentOrderRepository.save(payment);

        // Ledger Entry for Refund
        ledgerService.recordEntry(
                payment.getBookingId(),
                payment.getPaymentId(),
                payment.getTransactionId(),
                payment.getDriverId(),
                payment.getCustomerId(),
                LedgerType.REFUND,
                "DEBIT",
                amountToRefund,
                0.0,
                "Refund processed: " + reason,
                savedRefund.getRefundId(),
                "ADMIN"
        );

        // Adjust Driver Earnings if applicable
        driverEarningsRepository.findByBookingId(payment.getBookingId()).ifPresent(earnings -> {
            earnings.setPaymentStatus("REFUNDED");
            driverEarningsRepository.save(earnings);
        });

        return savedRefund;
    }
}
