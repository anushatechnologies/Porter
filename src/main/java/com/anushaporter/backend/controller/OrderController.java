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

    @PostMapping("/{id}/assign")
    public ResponseEntity<Map<String, Object>> assignDriver(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        return repository.findById(id).map(order -> {
            String driverIdStr = payload.get("driverId");
            if (driverIdStr != null) {
                try {
                    Long driverId = Long.valueOf(driverIdStr);
                    driverRepository.findById(driverId).ifPresent(driver -> {
                        order.setDriverId(driverId.toString());
                        order.setDriverEmail(driver.getEmail());
                        order.setDriverName(driver.getName());
                        order.setDriverPhone(driver.getPhone());
                        order.setDriverVehicleNumber(driver.getVehicleNumber());
                    });
                } catch (NumberFormatException e) {
                    // Ignore or handle
                }
            } else {
                order.setDriverName(payload.get("driverName"));
                order.setDriverPhone(payload.get("driverPhone"));
                order.setDriverVehicleNumber(payload.get("driverVehicleNumber"));
            }
            order.setStatus("assigned");
            Order savedOrder = repository.save(order);
            pushNotificationService.notifyOrderStatus(savedOrder, savedOrder.getStatus());
            return ResponseEntity.ok(Map.of("success", true, "order", savedOrder));
        }).orElse(ResponseEntity.notFound().build());
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
            String driverId = driver != null ? driver.getId().toString() : (payload != null ? payload.get("driverId") : null);
            String driverName = driver != null ? driver.getName() : (payload != null ? payload.get("driverName") : null);
            String driverEmail = driver != null ? driver.getEmail() : (payload != null ? payload.get("driverEmail") : null);
            String driverPhone = driver != null ? driver.getPhone() : (payload != null ? payload.get("driverPhone") : null);
            String driverVehicle = driver != null ? driver.getVehicleNumber() : (payload != null ? payload.get("driverVehicleNumber") : null);

            String currentStatus = order.getStatus() != null ? order.getStatus().toLowerCase() : "searching";
            boolean isClaimable = currentStatus.equals("searching") || currentStatus.equals("pending");

            boolean isSameDriver = (driverId != null && driverId.equals(order.getDriverId()))
                    || (driverEmail != null && driverEmail.equalsIgnoreCase(order.getDriverEmail()));

            if (!isClaimable && !isSameDriver) {
                return ResponseEntity.status(409).body(Map.of(
                        "success", false,
                        "message", "This order has already been accepted by another driver."
                ));
            }

            int rows = repository.claimOrderByIdAtomic(order.getId(), driverId, driverName, driverEmail, driverPhone, driverVehicle);
            if (rows == 0 && !isSameDriver) {
                return ResponseEntity.status(409).body(Map.of(
                        "success", false,
                        "message", "This order has already been accepted by another driver."
                ));
            }

            order.setDriverId(driverId);
            order.setDriverName(driverName);
            order.setDriverEmail(driverEmail);
            order.setDriverPhone(driverPhone);
            order.setDriverVehicleNumber(driverVehicle);
            order.setStatus("accepted");

            Order savedOrder = repository.findById(order.getId()).orElse(order);
            pushNotificationService.notifyOrderStatus(savedOrder, savedOrder.getStatus());
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Order accepted successfully",
                    "order", savedOrder
            ));
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

        String inputOtp = payload != null ? payload.get("otp") : null;
        if (inputOtp == null && payload != null) inputOtp = payload.get("deliveryOtp");

        Driver driver = driverAuthService.resolveAuthenticatedDriver(request);
        Map<String, Object> result = deliveryCompletionService.verifyOtp(id, inputOtp, driver);

        int httpStatus = result.containsKey("httpStatus") ? (int) result.get("httpStatus") : 200;
        result.remove("httpStatus");
        return ResponseEntity.status(httpStatus).body(result);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Step 2 — POST /api/orders/:orderId/complete
    //
    // Confirms payment and finalises the delivery.  Pre-requisite: order must
    // be in OTP_VERIFIED or PAYMENT_CONFIRMATION_PENDING status.
    // Idempotency-Key header prevents duplicate completions.
    // ──────────────────────────────────────────────────────────────────────────
    @RequestMapping(value = "/{id}/complete", method = {RequestMethod.POST, RequestMethod.PUT})
    public ResponseEntity<?> completeDelivery(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> payload,
            HttpServletRequest request) {

        String idempotencyKey = request.getHeader("Idempotency-Key");

        String paymentMethod = null;
        Double amount        = null;
        if (payload != null) {
            paymentMethod = (String) payload.get("paymentMethod");
            Object rawAmount = payload.get("amount");
            if (rawAmount instanceof Number) {
                amount = ((Number) rawAmount).doubleValue();
            } else if (rawAmount instanceof String) {
                try { amount = Double.parseDouble((String) rawAmount); } catch (NumberFormatException ignored) {}
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

    @RequestMapping(value = {"/{id}/accept", "/{id}/accept-order"}, method = {RequestMethod.PUT, RequestMethod.POST})
    public ResponseEntity<?> acceptOrder(@PathVariable String id, HttpServletRequest request) {
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
        com.anushaporter.backend.model.Driver driver = driverAuthService.resolveAuthenticatedDriver(request);

        String driverId = driver != null ? driver.getId().toString() : null;
        String driverName = driver != null ? driver.getName() : null;
        String driverEmail = driver != null ? driver.getEmail() : null;
        String driverPhone = driver != null ? driver.getPhone() : null;
        String driverVehicle = driver != null ? driver.getVehicleNumber() : null;

        if (driver == null) {
            String email = (String) request.getAttribute("userId");
            if (email != null) {
                AppUser user = appUserRepository.findFirstByEmailOrderByIdDesc(email).orElse(null);
                if (user != null) {
                    driver = driverRepository.findByPhone(user.getPhone()).orElse(null);
                    if (driver != null) {
                        driverId = driver.getId().toString();
                        driverName = driver.getName();
                        driverEmail = driver.getEmail();
                        driverPhone = driver.getPhone();
                        driverVehicle = driver.getVehicleNumber();
                    }
                }
            }
        }

        if (driverId == null && driverEmail == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "Unauthorized or driver profile not found"));
        }

        String currentStatus = order.getStatus() != null ? order.getStatus().toLowerCase() : "searching";
        boolean isClaimable = currentStatus.equals("searching") || currentStatus.equals("pending");

        boolean isSameDriver = (driverId != null && driverId.equals(order.getDriverId()))
                || (driverEmail != null && driverEmail.equalsIgnoreCase(order.getDriverEmail()));

        if (!isClaimable && !isSameDriver) {
            return ResponseEntity.status(409).body(Map.of(
                    "success", false,
                    "message", "This order has already been accepted by another driver."
            ));
        }

        int rows = repository.claimOrderByIdAtomic(order.getId(), driverId, driverName, driverEmail, driverPhone, driverVehicle);
        if (rows == 0 && !isSameDriver) {
            return ResponseEntity.status(409).body(Map.of(
                    "success", false,
                    "message", "This order has already been accepted by another driver."
            ));
        }

        order.setDriverId(driverId);
        order.setDriverName(driverName);
        order.setDriverEmail(driverEmail);
        order.setDriverPhone(driverPhone);
        order.setDriverVehicleNumber(driverVehicle);
        order.setStatus("accepted");

        Order savedOrder = repository.findById(order.getId()).orElse(order);
        pushNotificationService.notifyOrderStatus(savedOrder, savedOrder.getStatus());
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Order accepted successfully",
                "order", savedOrder,
                "orderDetails", savedOrder
        ));
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
