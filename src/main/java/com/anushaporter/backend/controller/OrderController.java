package com.anushaporter.backend.controller;

import com.anushaporter.backend.model.Order;
import com.anushaporter.backend.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.stream.Collectors;
import jakarta.servlet.http.HttpServletRequest;
import com.anushaporter.backend.model.AppUser;
import com.anushaporter.backend.model.Driver;
import com.anushaporter.backend.repository.AppUserRepository;
import com.anushaporter.backend.service.DeliveryCompletionService;
import com.anushaporter.backend.service.PushNotificationService;

/**
 * Admin-facing CRUD endpoint for orders (GET /api/orders).
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {
    @Autowired
    private OrderRepository repository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private PushNotificationService pushNotificationService;

    @Autowired
    private com.anushaporter.backend.repository.DriverRepository driverRepository;

    @Autowired
    private com.anushaporter.backend.service.DriverAuthService driverAuthService;

    @Autowired
    private DeliveryCompletionService deliveryCompletionService;

    /**
     * GET /api/orders
     * Returns formatted orders list for Admin Dashboard, Orders view, and Live Dispatch screen.
     */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAll() {
        List<Order> orders = repository.findAll();

        List<Map<String, Object>> items = orders.stream().map(o -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", o.getId());
            map.put("bookingId", o.getBookingId() != null ? o.getBookingId() : "ORD-" + o.getId());

            // User/Customer info
            String userEmail = o.getUserEmail() != null ? o.getUserEmail() : "";
            AppUser user = appUserRepository.findFirstByEmailOrderByIdDesc(userEmail).orElse(null);
            String customerName = user != null && user.getName() != null ? user.getName() : (o.getReceiverName() != null ? o.getReceiverName() : "Customer");
            String userPhone = user != null && user.getPhone() != null ? user.getPhone() : (o.getReceiverPhone() != null ? o.getReceiverPhone() : "");

            map.put("customer", customerName);
            map.put("userEmail", userEmail);
            map.put("userPhone", userPhone);

            // Pickup & Drop
            map.put("pickup", Map.of(
                    "addressLine", o.getPickupAddress() != null ? o.getPickupAddress() : "",
                    "lat", o.getPickupLat() != null ? o.getPickupLat() : 17.4483,
                    "lng", o.getPickupLng() != null ? o.getPickupLng() : 78.3915
            ));
            map.put("drop", Map.of(
                    "addressLine", o.getDropAddress() != null ? o.getDropAddress() : "",
                    "lat", o.getDropLat() != null ? o.getDropLat() : 17.4560,
                    "lng", o.getDropLng() != null ? o.getDropLng() : 78.4000
            ));

            map.put("amount", o.getAmount() != null ? o.getAmount() : 0.0);
            map.put("status", o.getStatus() != null ? o.getStatus() : "searching");

            // Driver info
            map.put("driver", o.getDriverName() != null ? o.getDriverName() : null);
            map.put("driverEmail", o.getDriverEmail() != null ? o.getDriverEmail() : null);
            map.put("driverPhone", o.getDriverPhone() != null ? o.getDriverPhone() : null);
            map.put("driverVehicleNumber", o.getDriverVehicleNumber() != null ? o.getDriverVehicleNumber() : null);

            map.put("serviceName", o.getServiceName() != null ? o.getServiceName() : "Standard Delivery");
            map.put("createdAt", o.getCreatedAt() != null ? o.getCreatedAt().toString() : java.time.LocalDateTime.now().toString());

            // Timeline
            List<Map<String, String>> timeline = new ArrayList<>();
            timeline.add(Map.of("time", "10:30 AM", "text", "Order Placed"));
            if (o.getDriverName() != null) {
                timeline.add(Map.of("time", "10:32 AM", "text", "Driver Assigned (" + o.getDriverName() + ")"));
            }
            map.put("timeline", timeline);

            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(items);
    }

    @PostMapping
    public Order create(@RequestBody Order entity) {
        return repository.save(entity);
    }

    @Autowired
    private com.anushaporter.backend.service.DriverWalletService driverWalletService;

    @PostMapping("/{id}/assign")
    public ResponseEntity<?> assignDriver(@PathVariable String id, @RequestBody Map<String, Object> payload) {
        Optional<Order> orderOpt = repository.findByBookingId(id);
        if (orderOpt.isEmpty()) {
            try {
                orderOpt = repository.findById(Long.valueOf(id));
            } catch (NumberFormatException ignored) {}
        }

        if (orderOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "Order not found"));
        }

        Order order = orderOpt.get();
        String driverIdStr = payload.get("driverId") != null ? String.valueOf(payload.get("driverId")) : null;

        if (driverIdStr != null && !driverIdStr.isBlank()) {
            Driver driver = driverWalletService.findDriverEntity(driverIdStr);
            if (driver == null) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Driver not found: " + driverIdStr));
            }

            double walletBalance = driver.getWalletBalance() != null ? driver.getWalletBalance() : 0.0;
            if (walletBalance <= 0.0) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "INSUFFICIENT_WALLET_BALANCE",
                        "message", "Driver wallet balance is ₹0 or negative. Driver must recharge before taking orders."
                ));
            }

            Map<String, Object> result = driverWalletService.assignOrder(order, driver);
            pushNotificationService.notifyOrderStatus(order, order.getStatus());
            return ResponseEntity.ok(result);
        } else {
            // Legacy / direct name assign fallback
            order.setDriverName(payload.get("driverName") != null ? String.valueOf(payload.get("driverName")) : null);
            order.setDriverPhone(payload.get("driverPhone") != null ? String.valueOf(payload.get("driverPhone")) : null);
            order.setDriverVehicleNumber(payload.get("driverVehicleNumber") != null ? String.valueOf(payload.get("driverVehicleNumber")) : null);
            order.setStatus("assigned");
            Order savedOrder = repository.save(order);
            pushNotificationService.notifyOrderStatus(savedOrder, savedOrder.getStatus());
            return ResponseEntity.ok(Map.of("success", true, "order", savedOrder));
        }
    }

    @RequestMapping(value = "/{id}/status", method = {RequestMethod.PUT, RequestMethod.POST})
    public ResponseEntity<Map<String, Object>> updateStatus(@PathVariable String id, @RequestBody(required = false) Map<String, String> payload, HttpServletRequest request) {
        Optional<Order> orderOpt = repository.findByBookingId(id);
        if (orderOpt.isEmpty()) {
            try {
                orderOpt = repository.findById(Long.valueOf(id));
            } catch (NumberFormatException ignored) {}
        }

        if (orderOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "Order not found"));
        }

        Order order = orderOpt.get();
        String targetStatus = payload != null ? payload.get("status") : null;
        String inputOtp = payload != null ? payload.get("otp") : null;
        if (inputOtp == null && payload != null) inputOtp = payload.get("deliveryOtp");

        // ── 1. Atomic Single-Driver Locking on Order Acceptance ───────────────
        if ("accepted".equalsIgnoreCase(targetStatus) || "assigned".equalsIgnoreCase(targetStatus) || "driver_assigned".equalsIgnoreCase(targetStatus)) {
            com.anushaporter.backend.model.Driver driver = driverAuthService.resolveAuthenticatedDriver(request);
            String driverId = driver != null && driver.getId() != null ? driver.getId().toString() : (payload != null ? payload.get("driverId") : null);
            String driverName = driver != null ? driver.getName() : (payload != null ? payload.get("driverName") : null);
            String driverEmail = driver != null ? driver.getEmail() : (payload != null ? payload.get("driverEmail") : null);
            String driverPhone = driver != null ? driver.getPhone() : (payload != null ? payload.get("driverPhone") : null);
            String driverVehicle = driver != null ? driver.getVehicleNumber() : (payload != null ? payload.get("driverVehicleNumber") : null);

            boolean isSameDriver = (driverId != null && order.getDriverId() != null && driverId.trim().equalsIgnoreCase(order.getDriverId().trim()))
                    || (driverEmail != null && order.getDriverEmail() != null && driverEmail.trim().equalsIgnoreCase(order.getDriverEmail().trim()))
                    || (driverPhone != null && order.getDriverPhone() != null && driverPhone.trim().equals(order.getDriverPhone().trim()));

            if (isSameDriver) {
                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("success", true);
                resp.put("statusCode", 200);
                resp.put("message", "You have already accepted this order.");
                resp.put("order", order);
                return ResponseEntity.ok(resp);
            }

            if (!isOrderClaimable(order.getStatus())) {
                Map<String, Object> conflict = new LinkedHashMap<>();
                conflict.put("success", false);
                conflict.put("statusCode", 409);
                conflict.put("message", "This order has already been accepted by another driver partner.");
                Map<String, Object> orderSummary = new LinkedHashMap<>();
                orderSummary.put("id", order.getId());
                if (order.getBookingId() != null) orderSummary.put("bookingId", order.getBookingId());
                orderSummary.put("status", order.getStatus() != null ? order.getStatus() : "accepted");
                conflict.put("order", orderSummary);
                return ResponseEntity.status(409).body(conflict);
            }

            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            int rows = repository.claimOrderByIdAtomic(order.getId(), driverId, driverName, driverEmail, driverPhone, driverVehicle, now);
            if (rows == 0) {
                Order fresh = repository.findById(order.getId()).orElse(order);
                boolean freshIsSame = (driverId != null && driverId.equalsIgnoreCase(fresh.getDriverId()))
                        || (driverEmail != null && driverEmail.equalsIgnoreCase(fresh.getDriverEmail()))
                        || (driverPhone != null && driverPhone.equals(fresh.getDriverPhone()));

                if (freshIsSame) {
                    Map<String, Object> resp = new LinkedHashMap<>();
                    resp.put("success", true);
                    resp.put("statusCode", 200);
                    resp.put("message", "You have already accepted this order.");
                    resp.put("order", fresh);
                    return ResponseEntity.ok(resp);
                }

                Map<String, Object> conflict = new LinkedHashMap<>();
                conflict.put("success", false);
                conflict.put("statusCode", 409);
                conflict.put("message", "This order has already been accepted by another driver partner.");
                Map<String, Object> orderSummary = new LinkedHashMap<>();
                orderSummary.put("id", fresh.getId());
                if (fresh.getBookingId() != null) orderSummary.put("bookingId", fresh.getBookingId());
                orderSummary.put("status", fresh.getStatus() != null ? fresh.getStatus() : "accepted");
                conflict.put("order", orderSummary);
                return ResponseEntity.status(409).body(conflict);
            }

            order.setDriverId(driverId);
            order.setDriverName(driverName);
            order.setDriverEmail(driverEmail);
            order.setDriverPhone(driverPhone);
            order.setDriverVehicleNumber(driverVehicle);
            order.setStatus("accepted");
            order.setAcceptedAt(now);

            Order savedOrder = repository.findById(order.getId()).orElse(order);
            if (pushNotificationService != null) {
                pushNotificationService.notifyOrderStatus(savedOrder, savedOrder.getStatus());
            }

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("success", true);
            resp.put("statusCode", 200);
            resp.put("message", "Order accepted successfully");
            resp.put("order", savedOrder);
            return ResponseEntity.ok(resp);
        }

        // ── 2. OTP Verification for Delivery Completion ───────────────────────
        //
        // If the caller sends OTP, route to the dedicated verify-otp flow.
        // Direct jumps to 'delivered'/'completed' without OTP_VERIFIED are blocked.
        if ("delivered".equalsIgnoreCase(targetStatus) || "completed".equalsIgnoreCase(targetStatus)) {
            String currentStatusLower = order.getStatus() != null ? order.getStatus().toLowerCase() : "";
            boolean otpAlreadyVerified = currentStatusLower.equals("otp_verified")
                    || currentStatusLower.equals("payment_confirmation_pending");
            if (!otpAlreadyVerified) {
                return ResponseEntity.status(422).body(Map.of(
                        "success", false,
                        "message", "Direct completion blocked. Verify OTP first via POST /{orderId}/verify-otp, then confirm payment via POST /{orderId}/complete."
                ));
            }
        }

        if (inputOtp != null && !inputOtp.isBlank()) {
            Driver callerDriver = driverAuthService.resolveAuthenticatedDriver(request);
            Map<String, Object> otpResult = deliveryCompletionService.verifyOtp(id, inputOtp, callerDriver);
            int httpStatus = otpResult.containsKey("httpStatus") ? (int) otpResult.get("httpStatus") : 200;
            otpResult.remove("httpStatus");
            return ResponseEntity.status(httpStatus).body(otpResult);
        }

        if (targetStatus != null && !targetStatus.isBlank()) {
            order.setStatus(targetStatus);
            if ("completed".equalsIgnoreCase(targetStatus) || "delivered".equalsIgnoreCase(targetStatus)) {
                String driverId = order.getDriverId();
                if (driverId != null && !driverId.isBlank() && order.getAmount() != null && order.getAmount() > 0) {
                    try {
                        String bookingId = order.getBookingId() != null ? order.getBookingId() : String.valueOf(order.getId());
                        driverWalletService.deductCommissionOnCompletion(driverId, bookingId, order.getAmount());
                    } catch (Exception e) {
                        System.err.println("[Wallet] Warning: error deducting commission on status update: " + e.getMessage());
                    }
                }
            }
        }
        Order savedOrder = repository.save(order);
        pushNotificationService.notifyOrderStatus(savedOrder, savedOrder.getStatus());

        String msg = "completed".equalsIgnoreCase(savedOrder.getStatus()) || "delivered".equalsIgnoreCase(savedOrder.getStatus())
                ? "Delivery completed successfully"
                : "Status updated successfully";

        return ResponseEntity.ok(Map.of("success", true, "message", msg, "order", savedOrder));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Step 1 — POST /api/orders/:orderId/verify-otp
    //
    // Validates the customer-provided delivery OTP and transitions the order
    // status to OTP_VERIFIED.  Does NOT mark the booking as delivered.
    // ──────────────────────────────────────────────────────────────────────────
    @RequestMapping(value = "/{id}/verify-otp", method = {RequestMethod.POST, RequestMethod.PUT})
    public ResponseEntity<?> verifyDeliveryOtp(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, String> payload,
            HttpServletRequest request) {

        String inputOtp = null;
        if (payload != null) {
            inputOtp = payload.get("enteredOtp");
            if (inputOtp == null) inputOtp = payload.get("otp");
            if (inputOtp == null) inputOtp = payload.get("deliveryOtp");
        }

        Driver driver = driverAuthService.resolveAuthenticatedDriver(request);
        Map<String, Object> result = deliveryCompletionService.verifyOtp(id, inputOtp, driver);

        int httpStatus = result.containsKey("httpStatus") ? (int) result.get("httpStatus") : 200;
        result.remove("httpStatus");
        return ResponseEntity.status(httpStatus).body(result);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Step 2 — POST /api/orders/:orderId/complete, /api/orders/:orderId/confirm-payment
    //
    // Confirms payment and finalises the delivery.  Pre-requisite: order must
    // be in OTP_VERIFIED or PAYMENT_CONFIRMATION_PENDING status.
    // Idempotency-Key header prevents duplicate completions.
    // ──────────────────────────────────────────────────────────────────────────
    @RequestMapping(value = {"/{id}/complete", "/{id}/confirm-payment"}, method = {RequestMethod.POST, RequestMethod.PUT})
    public ResponseEntity<?> completeDelivery(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> payload,
            HttpServletRequest request) {

        String idempotencyKey = request.getHeader("Idempotency-Key");
        if (idempotencyKey == null) idempotencyKey = request.getHeader("idempotency-key");
        if (idempotencyKey == null) idempotencyKey = request.getHeader("X-Idempotency-Key");

        String paymentMethod = null;
        Double amount        = null;
        if (payload != null) {
            paymentMethod = payload.get("paymentMethod") != null ? String.valueOf(payload.get("paymentMethod"))
                    : payload.get("method") != null ? String.valueOf(payload.get("method")) : null;

            Object rawAmount = payload.get("amount");
            if (rawAmount instanceof Number) {
                amount = ((Number) rawAmount).doubleValue();
            } else if (rawAmount != null) {
                try { amount = Double.parseDouble(rawAmount.toString()); } catch (NumberFormatException ignored) {}
            }

            if (idempotencyKey == null && payload.get("idempotencyKey") != null) {
                idempotencyKey = String.valueOf(payload.get("idempotencyKey"));
            }
        }

        Driver driver = driverAuthService.resolveAuthenticatedDriver(request);
        Map<String, Object> result = deliveryCompletionService.confirmPaymentAndComplete(
                id, paymentMethod, amount, idempotencyKey, driver
        );

        int httpStatus = result.containsKey("httpStatus") ? (int) result.get("httpStatus") : 200;
        result.remove("httpStatus");
        return ResponseEntity.status(httpStatus).body(result);
    }

    private boolean isOrderClaimable(String status) {
        if (status == null || status.isBlank()) return true;
        String s = status.trim().toLowerCase();
        return s.equals("searching") || s.equals("pending") || s.equals("created")
                || s.equals("broadcasted") || s.equals("unassigned") || s.equals("placed")
                || s.equals("available");
    }

    @RequestMapping(value = {"/{id}/accept", "/{id}/accept-order"}, method = {RequestMethod.PUT, RequestMethod.POST})
    public ResponseEntity<?> acceptOrder(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> payload,
            HttpServletRequest request
    ) {
        Optional<Order> orderOpt = repository.findByBookingId(id);
        if (orderOpt.isEmpty()) {
            try {
                orderOpt = repository.findById(Long.valueOf(id));
            } catch (NumberFormatException ignored) {}
        }

        if (orderOpt.isEmpty()) {
            Map<String, Object> notFound = new LinkedHashMap<>();
            notFound.put("success", false);
            notFound.put("statusCode", 404);
            notFound.put("message", "Order not found or has expired.");
            return ResponseEntity.status(404).body(notFound);
        }

        Order order = orderOpt.get();
        com.anushaporter.backend.model.Driver driver = driverAuthService.resolveAuthenticatedDriver(request);

        String driverId = driver != null && driver.getId() != null ? driver.getId().toString() : (payload != null && payload.get("driverId") != null ? String.valueOf(payload.get("driverId")) : null);
        String driverName = driver != null ? driver.getName() : (payload != null && payload.get("driverName") != null ? String.valueOf(payload.get("driverName")) : null);
        String driverEmail = driver != null ? driver.getEmail() : (payload != null && payload.get("driverEmail") != null ? String.valueOf(payload.get("driverEmail")) : null);
        String driverPhone = driver != null ? driver.getPhone() : (payload != null && payload.get("driverPhone") != null ? String.valueOf(payload.get("driverPhone")) : null);
        String driverVehicle = driver != null ? driver.getVehicleNumber() : (payload != null && payload.get("driverVehicleNumber") != null ? String.valueOf(payload.get("driverVehicleNumber")) : (payload != null && payload.get("vehicleNumber") != null ? String.valueOf(payload.get("vehicleNumber")) : null));

        if (driver == null && driverId == null && driverEmail == null) {
            String email = (String) request.getAttribute("userId");
            if (email != null) {
                AppUser user = appUserRepository.findFirstByEmailOrderByIdDesc(email).orElse(null);
                if (user != null) {
                    driver = driverRepository.findByPhone(user.getPhone()).orElse(null);
                    if (driver != null) {
                        driverId = driver.getId() != null ? driver.getId().toString() : null;
                        driverName = driver.getName();
                        driverEmail = driver.getEmail();
                        driverPhone = driver.getPhone();
                        driverVehicle = driver.getVehicleNumber();
                    }
                }
            }
        }

        if (driverId == null && driverEmail == null) {
            Map<String, Object> unauth = new LinkedHashMap<>();
            unauth.put("success", false);
            unauth.put("statusCode", 401);
            unauth.put("message", "Driver profile not found or unauthorized");
            return ResponseEntity.status(401).body(unauth);
        }

        boolean isSameDriver = (driverId != null && order.getDriverId() != null && driverId.trim().equalsIgnoreCase(order.getDriverId().trim()))
                || (driverEmail != null && order.getDriverEmail() != null && driverEmail.trim().equalsIgnoreCase(order.getDriverEmail().trim()))
                || (driverPhone != null && order.getDriverPhone() != null && driverPhone.trim().equals(order.getDriverPhone().trim()));

        // If this same driver already claimed the order, return idempotent success
        if (isSameDriver) {
            Map<String, Object> idempotentSuccess = new LinkedHashMap<>();
            idempotentSuccess.put("success", true);
            idempotentSuccess.put("statusCode", 200);
            idempotentSuccess.put("message", "You have already accepted this order.");
            idempotentSuccess.put("order", order);
            return ResponseEntity.ok(idempotentSuccess);
        }

        // If order is not in a claimable status and not accepted by this driver -> 409 Conflict
        if (!isOrderClaimable(order.getStatus())) {
            Map<String, Object> conflict = new LinkedHashMap<>();
            conflict.put("success", false);
            conflict.put("statusCode", 409);
            conflict.put("message", "This order has already been accepted by another driver partner.");
            Map<String, Object> orderSummary = new LinkedHashMap<>();
            orderSummary.put("id", order.getId());
            if (order.getBookingId() != null) orderSummary.put("bookingId", order.getBookingId());
            orderSummary.put("status", order.getStatus() != null ? order.getStatus() : "accepted");
            conflict.put("order", orderSummary);
            return ResponseEntity.status(409).body(conflict);
        }

        // Atomic update with race condition prevention
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        int rows = repository.claimOrderByIdAtomic(order.getId(), driverId, driverName, driverEmail, driverPhone, driverVehicle, now);
        if (rows == 0) {
            Order fresh = repository.findById(order.getId()).orElse(order);
            boolean freshIsSame = (driverId != null && driverId.equalsIgnoreCase(fresh.getDriverId()))
                    || (driverEmail != null && driverEmail.equalsIgnoreCase(fresh.getDriverEmail()))
                    || (driverPhone != null && driverPhone.equals(fresh.getDriverPhone()));

            if (freshIsSame) {
                Map<String, Object> idempotentSuccess = new LinkedHashMap<>();
                idempotentSuccess.put("success", true);
                idempotentSuccess.put("statusCode", 200);
                idempotentSuccess.put("message", "You have already accepted this order.");
                idempotentSuccess.put("order", fresh);
                return ResponseEntity.ok(idempotentSuccess);
            }

            Map<String, Object> conflict = new LinkedHashMap<>();
            conflict.put("success", false);
            conflict.put("statusCode", 409);
            conflict.put("message", "This order has already been accepted by another driver partner.");
            Map<String, Object> orderSummary = new LinkedHashMap<>();
            orderSummary.put("id", fresh.getId());
            if (fresh.getBookingId() != null) orderSummary.put("bookingId", fresh.getBookingId());
            orderSummary.put("status", fresh.getStatus() != null ? fresh.getStatus() : "accepted");
            conflict.put("order", orderSummary);
            return ResponseEntity.status(409).body(conflict);
        }

        order.setDriverId(driverId);
        order.setDriverName(driverName);
        order.setDriverEmail(driverEmail);
        order.setDriverPhone(driverPhone);
        order.setDriverVehicleNumber(driverVehicle);
        order.setStatus("accepted");
        order.setAcceptedAt(now);

        Order savedOrder = repository.findById(order.getId()).orElse(order);
        if (pushNotificationService != null) {
            pushNotificationService.notifyOrderStatus(savedOrder, savedOrder.getStatus());
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("statusCode", 200);
        response.put("message", "Order accepted successfully");
        response.put("order", savedOrder);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/timeline")
    public ResponseEntity<?> getOrderTimeline(@PathVariable String id) {
        Optional<Order> orderOpt = repository.findByBookingId(id);
        if (orderOpt.isEmpty()) {
            try {
                orderOpt = repository.findById(Long.valueOf(id));
            } catch (NumberFormatException ignored) {}
        }

        if (orderOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "Order not found"));
        }

        Order o = orderOpt.get();
        List<Map<String, String>> timeline = new ArrayList<>();
        timeline.add(Map.of("stage", "Order Placed", "time", o.getCreatedAt() != null ? o.getCreatedAt().toString() : "Just now", "status", "completed"));

        if (o.getDriverName() != null) {
            timeline.add(Map.of("stage", "Driver Assigned", "time", "Within 2 mins", "status", "completed", "driver", o.getDriverName()));
        } else {
            timeline.add(Map.of("stage", "Assigning Driver", "time", "In progress", "status", "pending"));
        }

        String currentStatus = o.getStatus() != null ? o.getStatus().toLowerCase() : "searching";
        if ("picked_up".equals(currentStatus) || "in_transit".equals(currentStatus) || "delivered".equals(currentStatus) || "completed".equals(currentStatus)) {
            timeline.add(Map.of("stage", "Goods Picked Up", "time", "Completed", "status", "completed"));
        } else {
            timeline.add(Map.of("stage", "Goods Picked Up", "time", "Pending", "status", "pending"));
        }

        if ("delivered".equals(currentStatus) || "completed".equals(currentStatus)) {
            timeline.add(Map.of("stage", "Order Delivered", "time", "Completed", "status", "completed"));
        } else {
            timeline.add(Map.of("stage", "Order Delivered", "time", "Pending", "status", "pending"));
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "orderId", o.getBookingId() != null ? o.getBookingId() : "ORD-" + o.getId(),
                "status", currentStatus,
                "timeline", timeline
        ));
    }
}
