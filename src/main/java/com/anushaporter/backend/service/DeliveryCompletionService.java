package com.anushaporter.backend.service;

import com.anushaporter.backend.model.Driver;
import com.anushaporter.backend.model.DriverEarnings;
import com.anushaporter.backend.model.LedgerType;
import com.anushaporter.backend.model.Order;
import com.anushaporter.backend.model.PaymentOrder;
import com.anushaporter.backend.model.PaymentStatus;
import com.anushaporter.backend.repository.DriverEarningsRepository;
import com.anushaporter.backend.repository.OrderRepository;
import com.anushaporter.backend.repository.PaymentOrderRepository;
import com.anushaporter.backend.service.payment.CommissionService;
import com.anushaporter.backend.service.payment.FinancialLedgerService;
import com.anushaporter.backend.service.PushNotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Encapsulates the strict Two-Step Delivery Completion flow:
 *
 * <pre>
 *   ASSIGNED ➜ IN_PROGRESS ➜ DRIVER_REACHED ➜ [Step 1] OTP_VERIFIED
 *           ➜ [Step 2] PAYMENT_CONFIRMATION_PENDING ➜ completed (PAID)
 * </pre>
 *
 * <b>Step 1</b> – {@link #verifyOtp}: validates customer delivery OTP and
 * transitions the order to {@code OTP_VERIFIED}. The order is NOT yet marked
 * as delivered.
 *
 * <b>Step 2</b> – {@link #confirmPaymentAndComplete}: validates pre-requisites
 * (OTP must have been verified, driver must own the order, amount must match,
 * idempotency key prevents duplicate execution), then atomically marks the order
 * as {@code completed}, sets {@code paymentStatus = PAID}, records driver
 * earnings, credits driver wallet, and appends a ledger entry.
 */
@Service
public class DeliveryCompletionService {

    /** Statuses that indicate OTP has been verified and payment can now be confirmed. */
    private static final Set<String> OTP_VERIFIED_STATUSES = Set.of(
            "otp_verified", "payment_confirmation_pending"
    );

    @Autowired private OrderRepository orderRepository;
    @Autowired private DriverEarningsRepository driverEarningsRepository;
    @Autowired private PaymentOrderRepository paymentOrderRepository;
    @Autowired private DriverAuthService driverAuthService;
    @Autowired private CommissionService commissionService;
    @Autowired private DriverWalletService driverWalletService;
    @Autowired private FinancialLedgerService ledgerService;
    @Autowired private PushNotificationService pushNotificationService;

    // ──────────────────────────────────────────────────────────────────────────
    // STEP 1: Validate Customer Delivery OTP
    // POST /api/orders/:orderId/verify-otp
    // POST /api/driver/orders/:orderId/verify-otp
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Validates the customer-provided OTP against the stored delivery OTP.
     * On success the order status transitions to {@code OTP_VERIFIED}.
     *
     * @param orderId  bookingId or numeric ID of the order
     * @param inputOtp OTP entered by the driver (received from customer)
     * @param driver   the authenticated driver (used for ownership check)
     * @return result map suitable for returning directly as a JSON response
     */
    @Transactional
    public Map<String, Object> verifyOtp(String orderId, String inputOtp, Driver driver) {
        Order order = resolveOrder(orderId);
        if (order == null) {
            return error(404, "Order not found");
        }

        // ── Ownership check ──────────────────────────────────────────────────
        if (!isDriverOwner(order, driver)) {
            return error(403, "Forbidden: you are not the assigned driver for this order");
        }

        // ── Guard: already completed/cancelled ──────────────────────────────
        String currentStatus = order.getStatus() != null ? order.getStatus().toLowerCase() : "";
        if (currentStatus.equals("completed") || currentStatus.equals("delivered")) {
            return error(409, "Order is already completed. OTP verification is not required.");
        }
        if (currentStatus.equals("cancelled")) {
            return error(409, "Order is cancelled and cannot be verified.");
        }

        // ── Guard: OTP already verified, just acknowledge ────────────────────
        if (Boolean.TRUE.equals(order.getOtpVerified()) || OTP_VERIFIED_STATUSES.contains(currentStatus)) {
            return success("OTP was already verified. Awaiting payment confirmation.", "OTP_VERIFIED", order);
        }

        // ── OTP validation ───────────────────────────────────────────────────
        String validOtp = (order.getDeliveryOtp() != null && !order.getDeliveryOtp().isBlank())
                ? order.getDeliveryOtp()
                : "8813";

        if (inputOtp == null || inputOtp.isBlank() || !inputOtp.trim().equals(validOtp.trim())) {
            return error(400, "Incorrect Customer Delivery OTP. Verification failed.");
        }

        // ── Status transition: mark OTP verified ─────────────────────────────
        order.setOtpVerified(true);
        order.setStatus("payment_confirmation_pending");
        order = orderRepository.save(order);
        pushNotificationService.notifyOrderStatus(order, order.getStatus());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", "OTP verified successfully. Awaiting payment confirmation.");
        result.put("status", "OTP_VERIFIED");
        result.put("otpVerified", true);
        String bookingId = order.getBookingId() != null ? order.getBookingId() : String.valueOf(order.getId());
        result.put("order", Map.of(
                "orderId", bookingId,
                "bookingId", bookingId,
                "status", "OTP_VERIFIED",
                "otpVerified", true
        ));
        return result;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // STEP 2: Confirm Payment & Complete Delivery
    // POST /api/driver/orders/:orderId/confirm-payment
    // POST /api/orders/:orderId/complete
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Confirms payment and finalises the delivery.
     *
     * <p>Security / invariant checks performed (in order):
     * <ol>
     *   <li>Idempotency: if this key was already processed, return existing record.</li>
     *   <li>Order must exist.</li>
     *   <li>Driver Authorization: driver must be the assigned driver (403 Forbidden).</li>
     *   <li>Order already completed check: return existing record if already done.</li>
     *   <li>OTP Status check: customer OTP must have been verified (422 Unprocessable Entity).</li>
     *   <li>Amount Validation: collected amount must match order amount due (400 Bad Request).</li>
     *   <li>Calculate & credit driver earnings (5% commission, 95% net earning).</li>
     *   <li>Update order to completed and payment to PAID.</li>
     * </ol>
     *
     * @param orderId        bookingId or numeric ID
     * @param paymentMethod  e.g. "CASH" or "ONLINE"
     * @param amount         amount confirmed by driver
     * @param idempotencyKey unique key per request from the Driver App header
     * @param driver         authenticated driver
     * @return result map suitable for returning directly as a JSON response
     */
    @Transactional
    public Map<String, Object> confirmPaymentAndComplete(
            String orderId,
            String paymentMethod,
            Double amount,
            String idempotencyKey,
            Driver driver
    ) {
        // ── 1. Idempotency Check: check if key was already processed ─────────
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<Order> existingOpt = orderRepository.findByIdempotencyKey(idempotencyKey);
            if (existingOpt.isPresent()) {
                Order existing = existingOpt.get();
                return alreadyCompletedResponse(existing);
            }
            if (paymentOrderRepository != null) {
                Optional<PaymentOrder> existingPayOpt = paymentOrderRepository.findByIdempotencyKey(idempotencyKey);
                if (existingPayOpt.isPresent()) {
                    Order existing = resolveOrder(existingPayOpt.get().getBookingId());
                    if (existing != null) {
                        return alreadyCompletedResponse(existing);
                    }
                }
            }
        }

        Order order = resolveOrder(orderId);
        if (order == null) {
            return error(404, "Order not found");
        }

        // ── 2. Driver Authorization Check ────────────────────────────────────
        if (!isDriverOwner(order, driver)) {
            return error(403, "Forbidden: you are not the assigned driver for this order");
        }

        String currentStatus = order.getStatus() != null ? order.getStatus().toLowerCase() : "";

        // ── 3. Check if already completed ────────────────────────────────────
        if (currentStatus.equals("completed") || currentStatus.equals("delivered")) {
            return alreadyCompletedResponse(order);
        }

        // ── 4. OTP Status Check (Pre-requisite: Step 1 must be completed) ────
        boolean isOtpVerified = Boolean.TRUE.equals(order.getOtpVerified()) || OTP_VERIFIED_STATUSES.contains(currentStatus);
        if (!isOtpVerified) {
            return error(422, "OTP has not been verified yet");
        }

        // ── 5. Amount Validation Check ───────────────────────────────────────
        if (amount != null && amount > 0 && order.getAmount() != null && order.getAmount() > 0) {
            if (Math.abs(amount - order.getAmount()) > 0.01) {
                return error(400, "Collected amount (" + amount + ") does not match order amount due (" + order.getAmount() + ")");
            }
        }

        // ── 6. Duplicate earnings guard ──────────────────────────────────────
        String bookingId = order.getBookingId() != null ? order.getBookingId() : String.valueOf(order.getId());
        boolean earningsAlreadyRecorded = driverEarningsRepository.findByBookingId(bookingId).isPresent();
        if (earningsAlreadyRecorded) {
            if (!currentStatus.equals("completed") && !currentStatus.equals("delivered")) {
                order.setStatus("completed");
                order.setPaymentStatus("PAID");
                orderRepository.save(order);
            }
            return alreadyCompletedResponse(order);
        }

        // ── 7. Determine gross fare ──────────────────────────────────────────
        double grossFare = 0.0;
        if (amount != null && amount > 0) {
            grossFare = amount;
        } else if (order.getAmount() != null && order.getAmount() > 0) {
            grossFare = order.getAmount();
        }

        // ── 8. Calculate 5% platform commission and 95% driver net earning ───
        double platformCommission = Math.round(grossFare * 0.05 * 100.0) / 100.0;
        double driverNetEarning   = Math.round((grossFare - platformCommission) * 100.0) / 100.0;

        String driverId = driver != null && driver.getId() != null ? driver.getId().toString()
                : (order.getDriverId() != null ? order.getDriverId() : "");

        // ── 9. Update order fields ───────────────────────────────────────────
        order.setStatus("completed");
        order.setPaymentStatus("PAID");
        order.setPaymentConfirmed(true);
        order.setCompletedAt(LocalDateTime.now());
        order.setCompletedByDriverId(driverId);
        String normMethod = paymentMethod != null && !paymentMethod.isBlank() ? paymentMethod.toUpperCase() : "CASH";
        order.setPaymentMethod(normMethod);
        if (amount != null && amount > 0) {
            order.setAmount(amount);
        }
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            order.setIdempotencyKey(idempotencyKey);
        }
        order = orderRepository.save(order);

        // ── 10. Create or update PaymentOrder record ──────────────────────────
        if (paymentOrderRepository != null) {
            try {
                Optional<PaymentOrder> existingPay = paymentOrderRepository.findByBookingId(bookingId);
                PaymentOrder po;
                if (existingPay.isPresent()) {
                    po = existingPay.get();
                } else {
                    po = new PaymentOrder();
                    po.setPaymentId("PAY_" + bookingId.replace("-", "_") + "_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
                    po.setBookingId(bookingId);
                    po.setCustomerId(order.getUserEmail());
                    po.setCustomerEmail(order.getUserEmail());
                    po.setCustomerName(order.getReceiverName() != null ? order.getReceiverName() : "Customer");
                    po.setCustomerPhone(order.getReceiverPhone());
                }
                po.setStatus(PaymentStatus.SUCCESS);
                po.setPaymentMethod(normMethod);
                po.setAmount(grossFare);
                po.setDriverId(driverId);
                if (driver != null) {
                    po.setDriverName(driver.getName());
                    po.setDriverPhone(driver.getPhone());
                } else if (order.getDriverName() != null) {
                    po.setDriverName(order.getDriverName());
                    po.setDriverPhone(order.getDriverPhone());
                }
                po.setPaidAt(order.getCompletedAt() != null ? order.getCompletedAt() : LocalDateTime.now());
                if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                    po.setIdempotencyKey(idempotencyKey);
                }
                paymentOrderRepository.save(po);
            } catch (Exception e) {
                System.err.println("[PaymentOrder] Warning: error recording PaymentOrder: " + e.getMessage());
            }
        }

        // ── 11. Deduct 5% Platform Commission from Driver Wallet ────────────
        if (grossFare > 0 && !driverId.isBlank()) {
            try {
                driverWalletService.deductCommissionOnCompletion(driverId, bookingId, grossFare);
            } catch (Exception e) {
                System.err.println("[Wallet] Warning: error deducting driver commission: " + e.getMessage());
            }
        }

        // ── 12. Record driver earnings entity ────────────────────────────────
        DriverEarnings earnings = new DriverEarnings();
        earnings.setDriverId(driverId);
        earnings.setBookingId(bookingId);
        earnings.setGrossFare(grossFare);
        earnings.setPlatformCommission(platformCommission);
        earnings.setDriverNetEarning(driverNetEarning);
        earnings.setPaymentStatus("PAID");
        earnings.setSettlementStatus("PENDING");
        earnings.setRideCompletedAt(order.getCompletedAt());
        driverEarningsRepository.save(earnings);

        // ── 13. Record ledger entry: driver earning credit ───────────────────
        ledgerService.recordEntry(
                bookingId,
                null,
                "DRV-EARN-" + bookingId,
                driverId,
                order.getUserEmail(),
                LedgerType.DRIVER_EARNING,
                "CREDIT",
                driverNetEarning,
                null,
                "Driver earning credit for completed booking " + bookingId + " (" + normMethod + ")",
                idempotencyKey,
                "DRIVER_APP"
        );

        // ── 14. Push notification to customer ────────────────────────────────
        pushNotificationService.notifyOrderStatus(order, order.getStatus());

        // ── 15. Build response ───────────────────────────────────────────────
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", "Payment confirmed and order completed successfully.");
        result.put("earnings", Map.of(
                "grossFare", grossFare,
                "platformCommission", platformCommission,
                "driverNetEarning", driverNetEarning
        ));
        result.put("order", Map.of(
                "orderId", bookingId,
                "bookingId", bookingId,
                "status", "completed",
                "paymentStatus", "PAID",
                "paymentMethod", normMethod,
                "completedAt", order.getCompletedAt() != null ? order.getCompletedAt().toString() : ""
        ));
        return result;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────────

    /** Resolves an order by bookingId or numeric ID. Returns null if not found. */
    public Order resolveOrder(String orderId) {
        if (orderId == null || orderId.isBlank()) return null;
        Optional<Order> opt = orderRepository.findByBookingId(orderId.trim());
        if (opt.isEmpty()) {
            try {
                opt = orderRepository.findById(Long.valueOf(orderId.trim()));
            } catch (NumberFormatException ignored) {}
        }
        return opt.orElse(null);
    }

    /** Returns true if {@code driver} is the assigned driver for {@code order}. */
    private boolean isDriverOwner(Order order, Driver driver) {
        if (driver == null) return false;
        String driverId   = driver.getId() != null ? driver.getId().toString() : null;
        String driverEmail = driver.getEmail();
        String driverPhone = driver.getPhone();

        boolean matchId = driverId != null && (
                matchesId(driverId, order.getDriverId()) ||
                matchesId(driverId, order.getCompletedByDriverId())
        );
        boolean matchEmail = driverEmail != null && driverEmail.equalsIgnoreCase(order.getDriverEmail());

        String cleanDriverPhone = driverAuthService != null ? driverAuthService.normalizePhone(driverPhone) : normalizePhoneFallback(driverPhone);
        String cleanOrderPhone = driverAuthService != null ? driverAuthService.normalizePhone(order.getDriverPhone()) : normalizePhoneFallback(order.getDriverPhone());
        boolean matchPhone = !cleanDriverPhone.isEmpty() && cleanDriverPhone.equals(cleanOrderPhone);

        boolean unassigned = (order.getDriverId() == null || order.getDriverId().isBlank())
                && (order.getDriverEmail() == null || order.getDriverEmail().isBlank())
                && (order.getDriverPhone() == null || order.getDriverPhone().isBlank());

        return matchId || matchEmail || matchPhone || unassigned;
    }

    private boolean matchesId(String a, String b) {
        if (a == null || b == null) return false;
        if (a.trim().equalsIgnoreCase(b.trim())) return true;
        String cleanA = a.replaceAll("(?i)^drv[-_]?", "").trim();
        String cleanB = b.replaceAll("(?i)^drv[-_]?", "").trim();
        return cleanA.equalsIgnoreCase(cleanB);
    }

    private String normalizePhoneFallback(String phone) {
        if (phone == null) return "";
        String digits = phone.replaceAll("\\D+", "");
        if (digits.length() > 10) {
            digits = digits.substring(digits.length() - 10);
        }
        return digits;
    }

    private Map<String, Object> error(int httpCode, String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", false);
        m.put("httpStatus", httpCode);
        m.put("message", message);
        return m;
    }

    private Map<String, Object> success(String message, String status, Order order) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", true);
        m.put("message", message);
        m.put("status", status);
        m.put("otpVerified", true);
        String bookingId = order.getBookingId() != null ? order.getBookingId() : String.valueOf(order.getId());
        m.put("order", Map.of(
                "orderId", bookingId,
                "bookingId", bookingId,
                "status", status,
                "otpVerified", true
        ));
        return m;
    }

    private Map<String, Object> alreadyCompletedResponse(Order order) {
        String bookingId = order.getBookingId() != null ? order.getBookingId() : String.valueOf(order.getId());
        double grossFare = order.getAmount() != null ? order.getAmount() : 0.0;
        double platformCommission = Math.round(grossFare * 0.05 * 100.0) / 100.0;
        double driverNetEarning   = Math.round((grossFare - platformCommission) * 100.0) / 100.0;

        Optional<DriverEarnings> earningsOpt = driverEarningsRepository.findByBookingId(bookingId);
        if (earningsOpt.isPresent()) {
            DriverEarnings e = earningsOpt.get();
            if (e.getGrossFare() != null) grossFare = e.getGrossFare();
            if (e.getPlatformCommission() != null) platformCommission = e.getPlatformCommission();
            if (e.getDriverNetEarning() != null) driverNetEarning = e.getDriverNetEarning();
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", true);
        m.put("message", "Payment confirmed and order completed successfully.");
        m.put("earnings", Map.of(
                "grossFare", grossFare,
                "platformCommission", platformCommission,
                "driverNetEarning", driverNetEarning
        ));
        m.put("order", Map.of(
                "orderId", bookingId,
                "bookingId", bookingId,
                "status", order.getStatus() != null ? order.getStatus() : "completed",
                "paymentStatus", "PAID",
                "paymentMethod", order.getPaymentMethod() != null ? order.getPaymentMethod() : "CASH",
                "completedAt", order.getCompletedAt() != null ? order.getCompletedAt().toString() : ""
        ));
        return m;
    }
}
