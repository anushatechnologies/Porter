package com.anushaporter.backend.controller;

import com.anushaporter.backend.model.Order;
import com.anushaporter.backend.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import com.anushaporter.backend.model.AppUser;
import com.anushaporter.backend.repository.AppUserRepository;

import java.util.List;

/**
 * Legacy admin-facing CRUD endpoint for orders.
 * The main booking flow uses BookingController (/api/bookings).
 */
@RestController
@RequestMapping("/api/orders")

public class OrderController {
    @Autowired
    private OrderRepository repository;

    @Autowired
    private AppUserRepository appUserRepository;

    @GetMapping
    public List<Order> getAll() {
        return repository.findAll();
    }

    @PostMapping
    public Order create(@RequestBody Order entity) {
        return repository.save(entity);
    }

    @Autowired
    private com.anushaporter.backend.repository.DriverRepository driverRepository;

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
            return ResponseEntity.ok(Map.of("success", true, "order", savedOrder));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        return repository.findById(id).map(order -> {
            order.setStatus(payload.get("status"));
            Order savedOrder = repository.save(order);
            return ResponseEntity.ok(Map.of("success", (Object) true, "order", (Object) savedOrder));
        }).orElse(ResponseEntity.notFound().build());
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
            order.setStatus("picked_up"); // or "driver_assigned" depending on flow, but the requirement said "driver accepts... status updates"
            Order savedOrder = repository.save(order);
            return ResponseEntity.ok(Map.of("success", true, "orderDetails", savedOrder));
        }).orElse(ResponseEntity.notFound().build());
    }
}
