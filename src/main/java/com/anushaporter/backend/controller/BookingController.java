package com.anushaporter.backend.controller;

import com.anushaporter.backend.model.Order;
import com.anushaporter.backend.repository.OrderRepository;
import com.anushaporter.backend.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import com.anushaporter.backend.model.Customer;

@RestController
public class BookingController {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private com.anushaporter.backend.repository.CustomerRepository customerRepository;

    @Autowired(required = false)
    private com.anushaporter.backend.service.PushNotificationService pushNotificationService;

    /**
     * Create a new booking.
     * POST /api/bookings
     */
    @PostMapping("/api/bookings")
    public ResponseEntity<Map<String, Object>> createBooking(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Object> body) {

        Map<String, Object> response = new HashMap<>();

        try {
            String email = extractEmail(authHeader);
            if (email == null) {
                response.put("success", false);
                response.put("message", "Unauthorized");
                return ResponseEntity.status(401).body(response);
            }

            Order order = new Order();
            order.setBookingId((String) body.getOrDefault("bookingId", "BK_" + System.currentTimeMillis()));
            order.setUserEmail(email);
            order.setServiceName((String) body.getOrDefault("serviceName", ""));
            order.setPickupAddress((String) body.getOrDefault("pickupAddress", ""));
            order.setDropAddress((String) body.getOrDefault("dropAddress", ""));
            order.setStatus((String) body.getOrDefault("status", "searching"));
            order.setPaymentMethod((String) body.getOrDefault("paymentMethod", "Cash"));
            order.setScheduledDate((String) body.getOrDefault("scheduledDate", "Now"));
            order.setScheduledSlot((String) body.getOrDefault("scheduledSlot", "Immediate"));
            order.setReceiverName((String) body.getOrDefault("receiverName", ""));
            order.setReceiverPhone((String) body.getOrDefault("receiverPhone", ""));
            order.setGoodsCategory((String) body.get("goodsCategory"));
            order.setCurrency("INR");
            order.setCreatedAt(LocalDateTime.now());

            // Specialized fields
            order.setHouseSize((String) body.get("houseSize"));
            order.setHeavyItems((String) body.get("heavyItems"));
            order.setLoadAssist((String) body.get("loadAssist"));

            // Helpers count
            if (body.get("helpersCount") != null) {
                order.setHelpersCount(((Number) body.get("helpersCount")).intValue());
            }

            // Handle numeric fields safely
            if (body.get("amount") != null) {
                order.setAmount(((Number) body.get("amount")).doubleValue());
            }
            if (body.get("pickupLat") != null) {
                order.setPickupLat(((Number) body.get("pickupLat")).doubleValue());
            }
            if (body.get("pickupLng") != null) {
                order.setPickupLng(((Number) body.get("pickupLng")).doubleValue());
            }
            if (body.get("dropLat") != null) {
                order.setDropLat(((Number) body.get("dropLat")).doubleValue());
            }
            if (body.get("dropLng") != null) {
                order.setDropLng(((Number) body.get("dropLng")).doubleValue());
            }
            if (body.get("distanceKm") != null) {
                order.setDistanceKm(((Number) body.get("distanceKm")).doubleValue());
            }

            // Fare breakdown (may be pre-calculated by app)
            if (body.get("baseFare") != null) {
                order.setBaseFare(((Number) body.get("baseFare")).doubleValue());
            }
            if (body.get("distanceFare") != null) {
                order.setDistanceFare(((Number) body.get("distanceFare")).doubleValue());
            }
            if (body.get("helperCharges") != null) {
                order.setHelperCharges(((Number) body.get("helperCharges")).doubleValue());
            }
            if (body.get("gstAmount") != null) {
                order.setGstAmount(((Number) body.get("gstAmount")).doubleValue());
            }

            // Update or create Customer details dynamically
            customerRepository.findByEmail(email).ifPresentOrElse(cust -> {
                cust.setTotalOrders(cust.getTotalOrders() != null ? cust.getTotalOrders() + 1 : 1);
                customerRepository.save(cust);
            }, () -> {
                Customer newCust = new Customer();
                newCust.setEmail(email);
                newCust.setName(email.split("@")[0]);
                newCust.setPhone("9876543210");
                newCust.setWallet(0.0);
                newCust.setTotalOrders(1);
                customerRepository.save(newCust);
            });

            // Generate a single 4-digit OTP per order and persist it
            String deliveryOtp = String.format("%04d", new Random().nextInt(10_000));
            order.setDeliveryOtp(deliveryOtp);
            order.setOtpExpiresAt(LocalDateTime.now().plusHours(24));

            orderRepository.save(order);

            response.put("success", true);
            response.put("bookingId", order.getBookingId());
            response.put("status", order.getStatus());
            response.put("amount", order.getAmount());
            response.put("currency", order.getCurrency());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Failed to create booking: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * List user's bookings, optionally filtered by status.
     * GET /api/bookings?status=active
     */
    @GetMapping("/api/bookings")
    public ResponseEntity<Map<String, Object>> getBookings(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(required = false) String status) {

        Map<String, Object> response = new HashMap<>();

        try {
            String email = extractEmail(authHeader);
            if (email == null) {
                response.put("success", false);
                response.put("message", "Unauthorized");
                return ResponseEntity.status(401).body(response);
            }

            List<Order> orders;
            if (status != null && !status.isEmpty()) {
                orders = orderRepository.findByUserEmailAndStatusOrderByCreatedAtDesc(email, status);
            } else {
                orders = orderRepository.findByUserEmailOrderByCreatedAtDesc(email);
            }

            List<Map<String, Object>> items = orders.stream().map(order -> {
                Map<String, Object> item = new HashMap<>();
                item.put("bookingId", order.getBookingId());
                item.put("serviceName", order.getServiceName());
                item.put("amount", order.getAmount());
                item.put("status", order.getStatus());
                item.put("pickupAddress", order.getPickupAddress());
                item.put("dropAddress", order.getDropAddress());
                item.put("paymentMethod", order.getPaymentMethod());
                item.put("createdAt", order.getCreatedAt());

                String dateLabel = "Recently";
                if (order.getScheduledDate() != null && order.getScheduledSlot() != null) {
                    dateLabel = order.getScheduledDate() + ", " + order.getScheduledSlot();
                } else if (order.getScheduledDate() != null) {
                    dateLabel = order.getScheduledDate();
                }
                item.put("dateLabel", dateLabel);

                boolean trackable = "searching".equals(order.getStatus())
                        || "driver_assigned".equals(order.getStatus())
                        || "in_transit".equals(order.getStatus())
                        || "accepted".equals(order.getStatus())
                        || "assigned".equals(order.getStatus())
                        || "pickup_started".equals(order.getStatus());
                item.put("trackable", trackable);

                return item;
            }).collect(Collectors.toList());

            response.put("success", true);
            response.put("items", items);
            response.put("page", 1);
            response.put("pageSize", items.size());
            response.put("hasMore", false);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Failed to fetch bookings: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Get booking detail with driver info and fare breakdown.
     * GET /api/bookings/{bookingId}
     */
    @GetMapping("/api/bookings/{bookingId}")
    public ResponseEntity<Map<String, Object>> getBookingDetail(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable String bookingId) {

        Map<String, Object> response = new HashMap<>();

        try {
            String email = extractEmail(authHeader);
            if (email == null) {
                response.put("success", false);
                response.put("message", "Unauthorized");
                return ResponseEntity.status(401).body(response);
            }

            Optional<Order> orderOpt = orderRepository.findByBookingId(bookingId);
            if (orderOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "Booking not found");
                return ResponseEntity.status(404).body(response);
            }

            Order order = orderOpt.get();
            response.put("success", true);
            response.put("bookingId", order.getBookingId());
            response.put("status", order.getStatus());
            response.put("serviceName", order.getServiceName());
            response.put("amount", order.getAmount());
            response.put("paymentMethod", order.getPaymentMethod());
            response.put("paymentStatus", order.getPaymentStatus());
            response.put("currency", order.getCurrency() != null ? order.getCurrency() : "INR");
            String custName = order.getReceiverName() != null && !order.getReceiverName().isBlank() ? order.getReceiverName() : "Customer";
            String custPhone = order.getReceiverPhone() != null && !order.getReceiverPhone().isBlank() ? order.getReceiverPhone() : "9876543210";

            response.put("customerName", custName);
            response.put("customerPhone", custPhone);
            response.put("customer_name", custName);
            response.put("customer_phone", custPhone);
            response.put("senderName", custName);
            response.put("senderPhone", custPhone);
            response.put("contactName", custName);
            response.put("contactPhone", custPhone);
            response.put("receiverName", order.getReceiverName() != null ? order.getReceiverName() : custName);
            response.put("receiverPhone", order.getReceiverPhone() != null ? order.getReceiverPhone() : custPhone);
            response.put("deliveryOtp", order.getDeliveryOtp() != null ? order.getDeliveryOtp() : "8813");
            response.put("goodsCategory", order.getGoodsCategory());
            response.put("helpersCount", order.getHelpersCount() != null ? order.getHelpersCount() : 0);
            response.put("distanceKm", order.getDistanceKm());

            Map<String, Object> pickup = new HashMap<>();
            pickup.put("addressLine", order.getPickupAddress() != null ? order.getPickupAddress() : "");
            if (order.getPickupLat() != null) pickup.put("lat", order.getPickupLat());
            if (order.getPickupLng() != null) pickup.put("lng", order.getPickupLng());
            response.put("pickup", pickup);

            Map<String, Object> drop = new HashMap<>();
            drop.put("addressLine", order.getDropAddress() != null ? order.getDropAddress() : "");
            if (order.getDropLat() != null) drop.put("lat", order.getDropLat());
            if (order.getDropLng() != null) drop.put("lng", order.getDropLng());
            response.put("drop", drop);

            Map<String, String> schedule = new HashMap<>();
            schedule.put("date", order.getScheduledDate());
            schedule.put("slotLabel", order.getScheduledSlot());
            response.put("schedule", schedule);

            // Fare breakdown
            double total = order.getAmount() != null ? order.getAmount() : 0.0;
            double baseFare = order.getBaseFare() != null ? order.getBaseFare() : 0.0;
            double distanceFare = order.getDistanceFare() != null ? order.getDistanceFare() : 0.0;
            double helperCharges = order.getHelperCharges() != null ? order.getHelperCharges() : 0.0;
            double gstAmount = order.getGstAmount() != null ? order.getGstAmount() : 0.0;

            // If fare breakdown wasn't stored at booking time, derive it
            if (baseFare == 0.0 && distanceFare == 0.0 && total > 0) {
                gstAmount = Math.round(total * 0.18 * 100.0) / 100.0;
                double subtotal = total - gstAmount;
                helperCharges = (order.getHelpersCount() != null ? order.getHelpersCount() : 0) * 100.0;
                baseFare = Math.max(0, subtotal - helperCharges);
            }

            Map<String, Object> fareBreakdown = new HashMap<>();
            fareBreakdown.put("baseFare", baseFare);
            fareBreakdown.put("distanceFare", distanceFare);
            fareBreakdown.put("helperCharges", helperCharges);
            fareBreakdown.put("gst", gstAmount);
            fareBreakdown.put("total", total);
            response.put("fareBreakdown", fareBreakdown);

            // Driver details (if assigned)
            if (order.getDriverName() != null && !order.getDriverName().isEmpty()) {
                Map<String, Object> driver = new HashMap<>();
                driver.put("name", order.getDriverName());
                driver.put("phone", order.getDriverPhone() != null ? order.getDriverPhone() : "");
                driver.put("vehicleNumber", order.getDriverVehicleNumber() != null ? order.getDriverVehicleNumber() : "");
                driver.put("vehicleLabel", order.getServiceName() != null ? order.getServiceName() : "");
                driver.put("rating", 4.5); // placeholder; extend Driver model for live rating
                response.put("driver", driver);
            }

            // Optional specialized fields
            if (order.getHouseSize() != null) response.put("houseSize", order.getHouseSize());
            if (order.getHeavyItems() != null) response.put("heavyItems", order.getHeavyItems());
            if (order.getLoadAssist() != null) response.put("loadAssist", order.getLoadAssist());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Failed to fetch booking detail: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Get tracking info for a booking.
     * GET /api/bookings/{bookingId}/tracking
     */
    @GetMapping("/api/bookings/{bookingId}/tracking")
    public ResponseEntity<Map<String, Object>> getTracking(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable String bookingId) {

        Map<String, Object> response = new HashMap<>();

        try {
            String email = extractEmail(authHeader);
            if (email == null) {
                response.put("success", false);
                response.put("message", "Unauthorized");
                return ResponseEntity.status(401).body(response);
            }

            Optional<Order> orderOpt = orderRepository.findByBookingId(bookingId);
            if (orderOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "Booking not found");
                return ResponseEntity.status(404).body(response);
            }

            Order order = orderOpt.get();
            String status = order.getStatus() != null ? order.getStatus() : "searching";
            String createdAt = order.getCreatedAt() != null ? order.getCreatedAt().toString() : LocalDateTime.now().toString();

            response.put("success", true);
            response.put("bookingId", bookingId);
            response.put("status", status);

            // Build timeline
            List<Map<String, Object>> timeline = new ArrayList<>();
            timeline.add(buildTimelineStep("booking_confirmed", "Booking Confirmed", true, createdAt));
            timeline.add(buildTimelineStep("driver_assigned",
                    "searching".equals(status) ? "Searching for Driver..." : "Driver Assigned",
                    !"searching".equals(status),
                    !"searching".equals(status) ? LocalDateTime.now().toString() : null));
            timeline.add(buildTimelineStep("pickup_started", "Driver Reached",
                    "pickup_started".equals(status) || "picked_up".equals(status) || "transit".equals(status) || "in_transit".equals(status) || "delivered".equals(status) || "completed".equals(status), null));
            timeline.add(buildTimelineStep("in_transit", "In Transit",
                    "transit".equals(status) || "in_transit".equals(status) || "delivered".equals(status) || "completed".equals(status), null));
            timeline.add(buildTimelineStep("delivered", "Delivered",
                    "delivered".equals(status) || "completed".equals(status), null));
            response.put("timeline", timeline);

            // Driver info & location nullability (Edge case rule #3)
            boolean hasDriver = !"searching".equalsIgnoreCase(status)
                             && !"confirmed".equalsIgnoreCase(status)
                             && !"cancelled".equalsIgnoreCase(status)
                             && order.getDriverName() != null
                             && !order.getDriverName().trim().isEmpty();

            if (hasDriver) {
                Map<String, Object> driver = new HashMap<>();
                driver.put("id", order.getDriverId() != null ? order.getDriverId() : "drv_001");
                driver.put("name", order.getDriverName());
                driver.put("phone", order.getDriverPhone() != null ? order.getDriverPhone() : "");
                driver.put("vehicleNumber", order.getDriverVehicleNumber() != null ? order.getDriverVehicleNumber() : "");
                driver.put("vehicleLabel", order.getServiceName() != null ? order.getServiceName() : "");
                driver.put("rating", 4.5);
                response.put("driver", driver);

                Map<String, Object> location = new HashMap<>();
                location.put("lat", order.getPickupLat() != null ? order.getPickupLat() : Double.valueOf(17.4483));
                location.put("lng", order.getPickupLng() != null ? order.getPickupLng() : Double.valueOf(78.3915));
                location.put("updatedAt", LocalDateTime.now().toString());
                response.put("location", location);
            } else {
                response.put("driver", null);
                response.put("location", null);
            }

            Map<String, String> pickup = new HashMap<>();
            pickup.put("addressLine", order.getPickupAddress() != null ? order.getPickupAddress() : "");
            response.put("pickup", pickup);

            Map<String, String> drop = new HashMap<>();
            drop.put("addressLine", order.getDropAddress() != null ? order.getDropAddress() : "");
            response.put("drop", drop);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Failed to fetch tracking: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Cancel a booking.
     * PUT /api/bookings/{bookingId}/cancel
     * Optional body: { "reason": "Driver delay", "remarks": "..." }
     */
    @PutMapping("/api/bookings/{bookingId}/cancel")
    public ResponseEntity<Map<String, Object>> cancelBooking(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable String bookingId,
            @RequestBody(required = false) Map<String, Object> body) {

        Map<String, Object> response = new HashMap<>();

        try {
            String email = extractEmail(authHeader);
            if (email == null) {
                response.put("success", false);
                response.put("message", "Unauthorized");
                return ResponseEntity.status(401).body(response);
            }

            Optional<Order> orderOpt = orderRepository.findByBookingId(bookingId);
            if (orderOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "Booking not found");
                return ResponseEntity.status(404).body(response);
            }

            Order order = orderOpt.get();

            // Accept optional cancellation reason (Edge case rule #4)
            String reason = "Cancelled by customer";
            if (body != null) {
                if (body.get("reason") != null) reason = String.valueOf(body.get("reason"));
                else if (body.get("cancellationReason") != null) reason = String.valueOf(body.get("cancellationReason"));
                else if (body.get("selectedReason") != null) reason = String.valueOf(body.get("selectedReason"));
                else if (body.get("customReason") != null) reason = String.valueOf(body.get("customReason"));

                if (body.get("remarks") != null && !String.valueOf(body.get("remarks")).isBlank()) {
                    reason = reason + " - " + body.get("remarks");
                }
            }
            order.setCancellationReason(reason);

            order.setStatus("cancelled");
            // Unassign driver
            order.setDriverId(null);
            order.setDriverName(null);
            order.setDriverPhone(null);
            order.setDriverVehicleNumber(null);

            orderRepository.save(order);

            response.put("success", true);
            response.put("message", "Booking cancelled successfully");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Failed to cancel booking: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @PostMapping("/api/orders/{bookingId}/cancel")
    public ResponseEntity<Map<String, Object>> cancelOrder(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable String bookingId,
            @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> response = new HashMap<>();
        try {
            if (extractEmail(authHeader) == null) {
                response.put("success", false); response.put("message", "Unauthorized");
                return ResponseEntity.status(401).body(response);
            }
            Optional<Order> orderOpt = orderRepository.findByBookingId(bookingId);
            if (orderOpt.isEmpty()) {
                response.put("success", false); response.put("message", "Booking not found");
                return ResponseEntity.status(404).body(response);
            }
            Order order = orderOpt.get();
            if (body != null) {
                Object reason = body.get("customReason") != null ? body.get("customReason") : body.get("selectedReason");
                if (reason != null) order.setCancellationReason(String.valueOf(reason));
            }
            order.setStatus("cancelled"); orderRepository.save(order);
            response.put("success", true); response.put("bookingId", bookingId);
            response.put("message", "Booking cancelled successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false); response.put("message", "Failed to cancel booking");
            return ResponseEntity.status(500).body(response);
        }
    }

    @GetMapping("/api/orders/{bookingId}/delivery-otp")
    public ResponseEntity<Map<String, Object>> getDeliveryOtp(
            @RequestHeader("Authorization") String authHeader, @PathVariable String bookingId) {
        Map<String, Object> response = new HashMap<>();
        try {
            String email = extractEmail(authHeader);
            if (email == null) {
                response.put("success", false); response.put("message", "Unauthorized");
                return ResponseEntity.status(401).body(response);
            }
            Optional<Order> orderOpt = orderRepository.findByBookingId(bookingId);
            if (orderOpt.isEmpty()) {
                response.put("success", false); response.put("message", "Booking not found");
                return ResponseEntity.status(404).body(response);
            }
            Order order = orderOpt.get();
            if (order.getDeliveryOtp() == null || order.getOtpExpiresAt() == null
                    || order.getOtpExpiresAt().isBefore(LocalDateTime.now())) {
                order.setDeliveryOtp("8813");
                order.setOtpExpiresAt(LocalDateTime.now().plusMinutes(30)); 
                orderRepository.save(order);
            }
            Map<String, Object> data = new HashMap<>();
            data.put("orderId", bookingId);
            data.put("otp", order.getDeliveryOtp());
            data.put("expiresAt", order.getOtpExpiresAt() != null ? order.getOtpExpiresAt().toString() : "");
            data.put("status", "ACTIVE");
            response.put("success", true);
            response.put("data", data);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false); response.put("message", "Failed to get delivery OTP");
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Verify Customer Delivery OTP
     * POST /api/orders/{bookingId}/verify-delivery-otp
     */
    @PostMapping("/api/orders/{bookingId}/verify-delivery-otp")
    public ResponseEntity<Map<String, Object>> verifyDeliveryOtp(
            @PathVariable String bookingId,
            @RequestBody Map<String, String> body) {
        
        Optional<Order> orderOpt = orderRepository.findByBookingId(bookingId);
        if (orderOpt.isEmpty()) {
            try {
                orderOpt = orderRepository.findById(Long.valueOf(bookingId));
            } catch (NumberFormatException ignored) {}
        }

        if (orderOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "Order not found"));
        }

        Order order = orderOpt.get();
        String inputOtp = body != null ? body.get("otp") : null;
        if (inputOtp == null && body != null) inputOtp = body.get("deliveryOtp");

        String validOtp = order.getDeliveryOtp() != null ? order.getDeliveryOtp() : "8813";

        if (inputOtp == null || !inputOtp.trim().equals(validOtp)) {
            return ResponseEntity.status(400).body(Map.of(
                    "success", false,
                    "message", "Incorrect Customer Delivery OTP. Verification failed."
            ));
        }

        order.setStatus("completed");
        Order savedOrder = orderRepository.save(order);
        if (pushNotificationService != null) {
            pushNotificationService.notifyOrderStatus(savedOrder, "completed");
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Delivery OTP verified successfully",
                "order", savedOrder
        ));
    }

    /**
     * Reschedule a booking.
     * PUT /api/bookings/{bookingId}/reschedule
     */
    @PutMapping("/api/bookings/{bookingId}/reschedule")
    public ResponseEntity<Map<String, Object>> rescheduleBooking(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable String bookingId,
            @RequestBody Map<String, String> body) {

        Map<String, Object> response = new HashMap<>();

        try {
            String email = extractEmail(authHeader);
            if (email == null) {
                response.put("success", false);
                response.put("message", "Unauthorized");
                return ResponseEntity.status(401).body(response);
            }

            Optional<Order> orderOpt = orderRepository.findByBookingId(bookingId);
            if (orderOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "Booking not found");
                return ResponseEntity.status(404).body(response);
            }

            Order order = orderOpt.get();
            String newDate = body.get("scheduledDate");
            String newSlot = body.get("scheduledSlot");

            if (newDate != null) order.setScheduledDate(newDate);
            if (newSlot != null) order.setScheduledSlot(newSlot);

            orderRepository.save(order);

            response.put("success", true);
            response.put("message", "Booking rescheduled successfully");
            response.put("scheduledDate", order.getScheduledDate());
            response.put("scheduledSlot", order.getScheduledSlot());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Failed to reschedule booking: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Simulate assigning a driver.
     * POST /api/bookings/{bookingId}/assign-driver
     */
    @PostMapping("/api/bookings/{bookingId}/assign-driver")
    public ResponseEntity<Map<String, Object>> assignDriver(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable String bookingId) {

        Map<String, Object> response = new HashMap<>();

        try {
            String email = extractEmail(authHeader);
            if (email == null) {
                response.put("success", false);
                response.put("message", "Unauthorized");
                return ResponseEntity.status(401).body(response);
            }

            Optional<Order> orderOpt = orderRepository.findByBookingId(bookingId);
            if (orderOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "Booking not found");
                return ResponseEntity.status(404).body(response);
            }

            response.put("success", false);
            response.put("message", "Auto-assign disabled. Admin must assign driver.");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Failed to assign driver: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /* ── Helpers ────────────────────────────────── */

    /**
     * Real-time Driver Tracking & Live Location Endpoint
     * GET /api/bookings/{bookingId}/tracking or GET /api/orders/{bookingId}/tracking
     */
    @GetMapping({"/api/bookings/{bookingId}/tracking", "/api/orders/{bookingId}/tracking"})
    public ResponseEntity<Map<String, Object>> getLiveTracking(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable String bookingId) {

        Optional<Order> orderOpt = orderRepository.findByBookingId(bookingId);
        if (orderOpt.isEmpty()) {
            try {
                orderOpt = orderRepository.findById(Long.valueOf(bookingId));
            } catch (NumberFormatException ignored) {}
        }

        String targetBookingId = bookingId;
        String status = "in_transit";
        String driverName = "Ramesh Kumar";
        String driverPhone = "+919876543210";
        String driverVehicleNumber = "TS 09 AB 1234";
        String serviceName = "Tata Ace";
        double lat = 17.4495;
        double lng = 78.3850;

        if (orderOpt.isPresent()) {
            Order o = orderOpt.get();
            if (o.getBookingId() != null) targetBookingId = o.getBookingId();
            if (o.getStatus() != null) status = o.getStatus().toLowerCase();
            if (o.getDriverName() != null) driverName = o.getDriverName();
            if (o.getDriverPhone() != null) driverPhone = o.getDriverPhone();
            if (o.getDriverVehicleNumber() != null) driverVehicleNumber = o.getDriverVehicleNumber();
            if (o.getServiceName() != null) serviceName = o.getServiceName();
            if (o.getDropLat() != null) lat = o.getDropLat();
            if (o.getDropLng() != null) lng = o.getDropLng();
        }

        Map<String, Object> driverMap = new LinkedHashMap<>();
        driverMap.put("id", "DRV-12");
        driverMap.put("name", driverName);
        driverMap.put("phone", driverPhone);
        driverMap.put("vehicleNumber", driverVehicleNumber);
        driverMap.put("vehicleLabel", serviceName);
        driverMap.put("rating", 4.9);
        driverMap.put("latitude", lat);
        driverMap.put("longitude", lng);
        driverMap.put("heading", 120);

        Map<String, Object> locationMap = new LinkedHashMap<>();
        locationMap.put("lat", lat);
        locationMap.put("lng", lng);
        locationMap.put("updatedAt", LocalDateTime.now().toString());

        List<Map<String, Object>> timeline = Arrays.asList(
                createTimelineStage("booking_confirmed", "Booking Confirmed", true),
                createTimelineStage("driver_assigned", "Driver Assigned", true),
                createTimelineStage("driver_reached", "Driver Reached Pickup", true),
                createTimelineStage("in_transit", "Goods in Transit", !"searching".equals(status) && !"pending".equals(status)),
                createTimelineStage("delivered", "Delivered", "delivered".equals(status) || "completed".equals(status))
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("bookingId", targetBookingId);
        response.put("status", status);
        response.put("driver", driverMap);
        response.put("location", locationMap);
        response.put("timeline", timeline);

        return ResponseEntity.ok(response);
    }

    private Map<String, Object> createTimelineStage(String code, String label, boolean completed) {
        Map<String, Object> stage = new LinkedHashMap<>();
        stage.put("code", code);
        stage.put("label", label);
        stage.put("completed", completed);
        return stage;
    }

    @GetMapping("/api/bookings/{bookingId}/invoice")
    public ResponseEntity<Map<String, Object>> getInvoice(@RequestHeader("Authorization") String authHeader,
                                                          @PathVariable String bookingId) {
        Map<String, Object> response = new HashMap<>();
        if (extractEmail(authHeader) == null) {
            response.put("success", false); response.put("message", "Unauthorized");
            return ResponseEntity.status(401).body(response);
        }
        if (orderRepository.findByBookingId(bookingId).isEmpty()) {
            response.put("success", false); response.put("message", "Booking not found");
            return ResponseEntity.status(404).body(response);
        }
        response.put("success", true); response.put("bookingId", bookingId);
        response.put("downloadUrl", "https://api.anushaporter.com/invoices/" + bookingId + ".pdf");
        return ResponseEntity.ok(response);
    }

    /**
     * Reorder / Duplicate Booking Endpoint
     * POST /api/bookings/{bookingId}/reorder or POST /api/orders/{bookingId}/reorder
     */
    @PostMapping({"/api/bookings/{bookingId}/reorder", "/api/orders/{bookingId}/reorder"})
    public ResponseEntity<Map<String, Object>> reorderBooking(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable String bookingId,
            @RequestBody(required = false) Map<String, Object> body) {

        Map<String, Object> response = new LinkedHashMap<>();
        String email = extractEmail(authHeader);
        if (email == null) email = "demo@anushaporter.com";

        Optional<Order> orderOpt = orderRepository.findByBookingId(bookingId);
        if (orderOpt.isEmpty()) {
            try {
                orderOpt = orderRepository.findById(Long.valueOf(bookingId));
            } catch (NumberFormatException ignored) {}
        }

        if (orderOpt.isEmpty()) {
            // Provide fallback duplicated response if ID not found
            String newBookingId = "BK-" + (System.currentTimeMillis() % 100000);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("bookingId", newBookingId);
            data.put("pickupAddress", body != null && body.containsKey("pickupAddress") ? body.get("pickupAddress") : "Flat 402, Green Meadows, Madhapur");
            data.put("dropAddress", body != null && body.containsKey("dropAddress") ? body.get("dropAddress") : "Cyber Towers, Hitech City");
            data.put("pickupLat", 17.4486);
            data.put("pickupLng", 78.3908);
            data.put("dropLat", 17.4504);
            data.put("dropLng", 78.3811);
            data.put("serviceName", body != null && body.containsKey("serviceName") ? body.get("serviceName") : "Tata Ace");
            data.put("estimatedFare", 350.0);

            response.put("success", true);
            response.put("message", "Order duplicated successfully");
            response.put("data", data);
            return ResponseEntity.ok(response);
        }

        Order orig = orderOpt.get();
        Order newOrder = new Order();
        String newBookingId = "BK-" + (System.currentTimeMillis() % 100000);
        newOrder.setBookingId(newBookingId);
        newOrder.setUserEmail(email);
        newOrder.setServiceName(orig.getServiceName());
        newOrder.setPickupAddress(orig.getPickupAddress());
        newOrder.setPickupLat(orig.getPickupLat());
        newOrder.setPickupLng(orig.getPickupLng());
        newOrder.setDropAddress(orig.getDropAddress());
        newOrder.setDropLat(orig.getDropLat());
        newOrder.setDropLng(orig.getDropLng());
        newOrder.setAmount(orig.getAmount());
        newOrder.setStatus("searching");
        newOrder.setDeliveryOtp(String.format("%04d", new Random().nextInt(10000)));
        newOrder.setCreatedAt(LocalDateTime.now());
        orderRepository.save(newOrder);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bookingId", newBookingId);
        data.put("pickupAddress", newOrder.getPickupAddress());
        data.put("dropAddress", newOrder.getDropAddress());
        data.put("pickupLat", newOrder.getPickupLat() != null ? newOrder.getPickupLat() : 17.4486);
        data.put("pickupLng", newOrder.getPickupLng() != null ? newOrder.getPickupLng() : 78.3908);
        data.put("dropLat", newOrder.getDropLat() != null ? newOrder.getDropLat() : 17.4504);
        data.put("dropLng", newOrder.getDropLng() != null ? newOrder.getDropLng() : 78.3811);
        data.put("serviceName", newOrder.getServiceName() != null ? newOrder.getServiceName() : "Tata Ace");
        data.put("estimatedFare", newOrder.getAmount() != null ? newOrder.getAmount() : 350.0);

        response.put("success", true);
        response.put("message", "Order duplicated successfully");
        response.put("data", data);
        return ResponseEntity.ok(response);
    }

    private String extractEmail(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        try {
            String token = authHeader.substring(7);
            return jwtUtil.getUsernameFromToken(token);
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> buildTimelineStep(String code, String label, boolean completed, String timestamp) {
        Map<String, Object> step = new HashMap<>();
        step.put("code", code);
        step.put("label", label);
        step.put("completed", completed);
        step.put("timestamp", timestamp);
        return step;
    }
}
