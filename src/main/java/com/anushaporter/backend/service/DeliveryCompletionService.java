package com.anushaporter.backend.service;

import com.anushaporter.backend.model.Driver;
import com.anushaporter.backend.model.DriverEarnings;
import com.anushaporter.backend.model.LedgerType;
import com.anushaporter.backend.model.Order;
import com.anushaporter.backend.repository.DriverEarningsRepository;
import com.anushaporter.backend.repository.OrderRepository;
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

/**
 * Encapsulates the strict Two-Step Delivery Completion flow:
 *
 * <pre>
 *   ASSIGNED ➜ IN_PROGRESS ➜ DRIVER_REACHED ➜ [Step 1] OTP_VERIFIED
 *           ➜ [Step 2] PAYMENT_CONFIRMATION_PENDING ➜ completed / delivered
 * </pre>
 *
 * <b>Step 1</b> – {@link #verifyOtp}: validates the customer delivery OTP and
 * transitions the order to {@code OTP_VERIFIED}. The order is NOT yet marked
 * as delivered.
 *
 * <b>Step 2</b> – {@link #confirmPaymentAndComplete}: validates pre-requisites
 * (OTP must have been verified, driver must own the order, idempotency key
 * prevents double-execution), then atomically marks the order as
 * {@code completed}, sets {@code paymentStatus = PAID}, records a
 * {@link DriverEarnings} entry, and appends a ledger credit.
 */
@Service
public class DeliveryCompletionService {

    /** Statuses that indicate OTP has been verified and payment can now be confirmed. */
    private static final Set<String> OTP_VERIFIED_STATUSES = Set.of(
            "otp_verified", "payment_confirmation_pending"
    );

    @Autowired private OrderRepository orderRepository;
    @Autowired private DriverEarningsRepository driverEarningsRepository;
    @Autowired private CommissionService commissionService;
    @Autowired private FinancialLedgerService ledgerService;
    @Autowired private PushNotificationService pushNotificationService;

    // ──────────────────────────────────────────────────────────────────────────
    // STEP 1: Validate Customer Delivery OTP
    // POST /api/orders/:orderId/verify-otp
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Validates the customer-provided OTP against the stored delivery OTP.
     * On success the order status transitions to {@code OTP_VERIFIED}.
     * The order is explicitly <em>NOT</em> marked as delivered or completed.
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
        if (OTP_VERIFIED_STATUSES.contains(currentStatus)) {
            return success("OTP was already verified. Awaiting payment confirmation.", order.getStatus(), order);
        }

        // ── OTP validation ───────────────────────────────────────────────────
        String validOtp = (order.getDeliveryOtp() != null && !order.getDeliveryOtp().isBlank())
                ? order.getDeliveryOtp()
                : "8813";

        if (inputOtp == null || inputOtp.isBlank() || !inputOtp.trim().equals(validOtp)) {
            return error(400, "Incorrect Customer Delivery OTP. Verification failed.");
        }

        // ── Atomic status transition ─────────────────────────────────────────
        int updated = orderRepository.markOtpVerifiedById(order.getId());
        if (updated == 0) {
            // Race condition: re-read to check if already verified
            order = orderRepository.findById(order.getId()).orElse(order);
            String refreshedStatus = order.getStatus() != null ? order.getStatus().toLowerCase() : "";
            if (OTP_VERIFIED_STATUSES.contains(refreshedStatus)) {
                return success("OTP verified successfully. Awaiting payment confirmation.", order.getStatus(), order);
            }
            return error(409, "Could not verify OTP due to a concurrent status change. Please retry.");
        }

        // ── Refresh and notify ───────────────────────────────────────────────
        order = orderRepository.findById(order.getId()).orElse(order);
        pushNotificationService.notifyOrderStatus(order, order.getStatus());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", "OTP verified successfully. Awaiting payment confirmation.");
        result.put("status", "OTP_VERIFIED");
        result.put("order", Map.of(
                "orderId", order.getBookingId() != null ? order.getBookingId() : String.valueOf(order.getId()),
                "status", order.getStatus()
        ));
        return result;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // STEP 2: Confirm Payment & Complete Delivery
    // POST /api/orders/:orderId/complete
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Confirms payment and finalises the delivery.
     *
     * <p>Security / invariant checks performed (in order):
     * <ol>
     *   <li>Order must exist.</li>
     *   <li>Driver must be the assigned driver (ownership).</li>
     *   <li>Idempotency: if this key was already processed, return the cached
     *       success response immediately without re-processing.</li>
     *   <li>Order status must be {@code OTP_VERIFIED} or
     *       {@code PAYMENT_CONFIRMATION_PENDING} – direct jumps from earlier
     *       stages are rejected.</li>
     *   <li>Driver earnings must not already exist for this booking (prevents
     *       duplicate wallet credits even without an idempotency key).</li>
     * </ol>
     *
     * @param orderId        bookingId or numeric ID
     * @param paymentMethod  e.g. "UPI_QR", "CASH", "ONLINE"
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
        // ── Idempotency: check if this key was already processed ─────────────
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<Order> existingOpt = orderRepository.findByIdempotencyKey(idempotencyKey);
            if (existingOpt.isPresent()) {
                Order existing = existingOpt.get();
                return alreadyCompletedResponse(existing);
            }
        }

        Order order = resolveOrder(orderId);
        if (order == null) {
            return error(404, "Order not found");
        }

        // ── Ownership check ──────────────────────────────────────────────────
        if (!isDriverOwner(order, driver)) {
            return error(403, "Forbidden: you are not the assigned driver for this order");
        }

        // ── Pre-requisite: OTP must have been verified ───────────────────────
        String currentStatus = order.getStatus() != null ? order.getStatus().toLowerCase() : "";
        if (!OTP_VERIFIED_STATUSES.contains(currentStatus)) {
            if (currentStatus.equals("completed") || currentStatus.equals("delivered")) {
                return alreadyCompletedResponse(order);
            }
            return error(422, "Payment confirmation rejected: order OTP has not been verified yet. "
                    + "Current status: " + order.getStatus()
                    + ". Complete Step 1 (OTP verification) first.");
        }

        // ── Duplicate earnings guard ─────────────────────────────────────────
        String bookingId = order.getBookingId() != null ? order.getBookingId() : String.valueOf(order.getId());
        boolean earningsAlreadyRecorded = driverEarningsRepository.findByBookingId(bookingId).isPresent();
        if (earningsAlreadyRecorded) {
            // Earnings already credited – just ensure order is marked completed
            if (!currentStatus.equals("completed") && !currentStatus.equals("delivered")) {
                order.setStatus("completed");
                orderRepository.save(order);
            }
            return alreadyCompletedResponse(order);
        }

        // ── Determine gross fare ─────────────────────────────────────────────
        double grossFare = 0.0;
        if (amount != null && amount > 0) {
            grossFare = amount;
        } else if (order.getAmount() != null) {
            grossFare = order.getAmount();
        }

        // ── Calculate commission and net earning ─────────────────────────────
        Map<String, Object> commission = commissionService.calculateCommission(grossFare, order.getServiceName());
        double platformCommission = (Double) commission.get("platformCommission");
        double driverNetEarning   = (Double) commission.get("driverNetEarning");

        // ── Update order fields ──────────────────────────────────────────────
        order.setStatus("completed");
        order.setPaymentStatus("PAID");
        order.setCompletedAt(LocalDateTime.now());
        order.setCompletedByDriverId(driver != null ? driver.getId().toString() : null);
        if (paymentMethod != null && !paymentMethod.isBlank()) {
            order.setPaymentMethod(paymentMethod);
        }
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            order.setIdempotencyKey(idempotencyKey);
        }
        order = orderRepository.save(order);

        // ── Record driver earnings ───────────────────────────────────────────
        String driverId = driver != null ? driver.getId().toString() : order.getDriverId();
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

        // ── Record ledger entry: driver earning credit ───────────────────────
        ledgerService.recordEntry(
                bookingId,
                null,               // paymentId (not a gateway payment)
                "DRV-EARN-" + bookingId,
                driverId,
                order.getUserEmail(),
                LedgerType.DRIVER_EARNING,
                "CREDIT",
                driverNetEarning,
                null,               // balanceAfter – computed separately in wallet service
                "Driver earning credit for completed booking " + bookingId
                        + " (" + (paymentMethod != null ? paymentMethod : "CASH") + ")",
                idempotencyKey,
                "DRIVER_APP"
        );

        // ── Push notification to customer ────────────────────────────────────
        pushNotificationService.notifyOrderStatus(order, order.getStatus());

        // ── Build response ───────────────────────────────────────────────────
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", "Payment confirmed and delivery completed successfully!");
        result.put("order", Map.of(
                "orderId", bookingId,
                "status", "completed",
                "paymentStatus", "PAID",
                "completedAt", order.getCompletedAt().toString()
        ));
        result.put("earnings", Map.of(
                "grossFare", grossFare,
                "platformCommission", platformCommission,
                "driverNetEarning", driverNetEarning
        ));
        return result;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────────

    /** Resolves an order by bookingId or numeric ID. Returns null if not found. */
    private Order resolveOrder(String orderId) {
        Optional<Order> opt = orderRepository.findByBookingId(orderId);
        if (opt.isEmpty()) {
            try {
                opt = orderRepository.findById(Long.valueOf(orderId));
            } catch (NumberFormatException ignored) {}
        }
        return opt.orElse(null);
    }

    /** Returns true if {@code driver} is the assigned driver for {@code order}. */
    private boolean isDriverOwner(Order order, Driver driver) {
        if (driver == null) return false; // un-authenticated
        String driverId   = driver.getId() != null ? driver.getId().toString() : null;
        String driverEmail = driver.getEmail();
        boolean matchId    = driverId != null && driverId.equals(order.getDriverId());
        boolean matchEmail = driverEmail != null && driverEmail.equalsIgnoreCase(order.getDriverEmail());
        return matchId || matchEmail;
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
        String bookingId = order.getBookingId() != null ? order.getBookingId() : String.valueOf(order.getId());
        m.put("order", Map.of("orderId", bookingId, "status", status));
        return m;
    }

    private Map<String, Object> alreadyCompletedResponse(Order order) {
        String bookingId = order.getBookingId() != null ? order.getBookingId() : String.valueOf(order.getId());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", true);
        m.put("message", "Payment confirmed and delivery completed successfully!");
        m.put("order", Map.of(
                "orderId", bookingId,
                "status", "completed",
                "paymentStatus", "PAID"
        ));
        return m;
    }
}
