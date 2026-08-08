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
            order.setCurrency("INR");
            order.setCreatedAt(LocalDateTime.now());
            
            // New specialized fields
            order.setHouseSize((String) body.get("houseSize"));
            order.setHeavyItems((String) body.get("heavyItems"));
            order.setLoadAssist((String) body.get("loadAssist"));

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

                String dateLabel = "Recently";
                if (order.getScheduledDate() != null && order.getScheduledSlot() != null) {
                    dateLabel = order.getScheduledDate() + ", " + order.getScheduledSlot();
                } else if (order.getScheduledDate() != null) {
                    dateLabel = order.getScheduledDate();
                }
                item.put("dateLabel", dateLabel);

                boolean trackable = "searching".equals(order.getStatus())
                        || "driver_assigned".equals(order.getStatus())
                        || "in_transit".equals(order.getStatus());
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
     * Get booking detail.
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

            Map<String, String> pickup = new HashMap<>();
            pickup.put("addressLine", order.getPickupAddress());
            response.put("pickup", pickup);

            Map<String, String> drop = new HashMap<>();
            drop.put("addressLine", order.getDropAddress());
            response.put("drop", drop);

            Map<String, String> schedule = new HashMap<>();
            schedule.put("date", order.getScheduledDate());
            schedule.put("slotLabel", order.getScheduledSlot());
            response.put("schedule", schedule);

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
            timeline.add(buildTimelineStep("pickup_started", "Pickup Started",
                    "pickup_started".equals(status) || "picked_up".equals(status) || "transit".equals(status) || "in_transit".equals(status) || "delivered".equals(status) || "completed".equals(status), null));
            timeline.add(buildTimelineStep("in_transit", "In Transit",
                    "transit".equals(status) || "in_transit".equals(status) || "delivered".equals(status) || "completed".equals(status), null));
            timeline.add(buildTimelineStep("delivered", "Delivered",
                    "delivered".equals(status) || "completed".equals(status), null));
            response.put("timeline", timeline);

            // Driver info
            if (!"searching".equals(status) && !"cancelled".equals(status)) {
                Map<String, String> driver = new HashMap<>();
                driver.put("id", "drv_001");
                driver.put("name", order.getDriverName() != null ? order.getDriverName() : "Driver");
                driver.put("phone", order.getDriverPhone() != null ? order.getDriverPhone() : "");
                driver.put("vehicleNumber", order.getDriverVehicleNumber() != null ? order.getDriverVehicleNumber() : "");
                driver.put("vehicleLabel", order.getServiceName() != null ? order.getServiceName() : "");
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
     */
    @PutMapping("/api/bookings/{bookingId}/cancel")
    public ResponseEntity<Map<String, Object>> cancelBooking(
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
            order.setStatus("cancelled");
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
                order.setDeliveryOtp(String.format("%04d", new Random().nextInt(10_000)));
                order.setOtpExpiresAt(LocalDateTime.now().plusMinutes(30)); orderRepository.save(order);
            }
            Map<String, Object> data = new HashMap<>(); data.put("orderId", bookingId);
            data.put("otp", order.getDeliveryOtp()); data.put("expiresAt", order.getOtpExpiresAt());
            data.put("status", "ACTIVE"); response.put("success", true); response.put("data", data);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false); response.put("message", "Failed to get delivery OTP");
            return ResponseEntity.status(500).body(response);
        }
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


            // Don't auto assign dummy driver anymore. This endpoint should just fail or do nothing
            // order.setStatus("driver_assigned");
            // order.setDriverName("Ramesh Kumar");
            // order.setDriverPhone("+91 9876543210");
            // order.setDriverVehicleNumber("TS 09 EU 1234");
            // orderRepository.save(order);

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
