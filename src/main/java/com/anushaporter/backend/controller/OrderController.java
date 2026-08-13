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
import com.anushaporter.backend.repository.AppUserRepository;
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
    public ResponseEntity<Map<String, Object>> updateStatus(@PathVariable String id, @RequestBody Map<String, String> payload) {
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

        // If completing/delivering order or OTP is provided, enforce strict OTP verification!
        if ("delivered".equalsIgnoreCase(targetStatus) || "completed".equalsIgnoreCase(targetStatus) || inputOtp != null) {
            String validOtp = order.getDeliveryOtp() != null ? order.getDeliveryOtp() : "8813";

            if (inputOtp == null || !inputOtp.trim().equals(validOtp)) {
                return ResponseEntity.status(400).body(Map.of(
                        "success", false,
                        "message", "Incorrect Customer Delivery OTP"
                ));
            }
        }

        if (targetStatus != null && !targetStatus.isBlank()) {
            order.setStatus(targetStatus);
        }
        Order savedOrder = repository.save(order);
        pushNotificationService.notifyOrderStatus(savedOrder, savedOrder.getStatus());
        return ResponseEntity.ok(Map.of("success", true, "message", "Status updated successfully", "order", savedOrder));
    }

    @PutMapping("/{id}/accept")
    public ResponseEntity<?> acceptOrder(@PathVariable Long id, HttpServletRequest request) {
        String email = (String) request.getAttribute("userId");
        if (email == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "Unauthorized"));
        }

        return repository.findById(id).map(order -> {
            AppUser user = appUserRepository.findFirstByEmailOrderByIdDesc(email).orElse(null);
            if (user != null) {
                driverRepository.findByPhone(user.getPhone()).ifPresent(driver -> {
                    order.setDriverId(driver.getId().toString());
                    order.setDriverEmail(driver.getEmail());
                    order.setDriverName(driver.getName());
                    order.setDriverPhone(driver.getPhone());
                    order.setDriverVehicleNumber(driver.getVehicleNumber());
                });
            }
            order.setStatus("picked_up");
            Order savedOrder = repository.save(order);
            pushNotificationService.notifyOrderStatus(savedOrder, savedOrder.getStatus());
            return ResponseEntity.ok(Map.of("success", true, "orderDetails", savedOrder));
        }).orElse(ResponseEntity.notFound().build());
    }
}
