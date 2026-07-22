package com.anushaporter.backend.controller;

import com.anushaporter.backend.model.Driver;
import com.anushaporter.backend.repository.DriverRepository;
import com.anushaporter.backend.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
public class AdminAPIController {

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private OrderRepository orderRepository;

    @GetMapping("/metrics")
    public ResponseEntity<?> getMetrics() {
        long totalDrivers = driverRepository.count();
        long pendingKyc = driverRepository.findAll().stream()
                .filter(d -> "pending".equalsIgnoreCase(d.getKyc()))
                .count();
        long activeOrders = orderRepository.findAll().stream()
                .filter(o -> "driver_assigned".equalsIgnoreCase(o.getStatus()) || "picked_up".equalsIgnoreCase(o.getStatus()))
                .count();

        return ResponseEntity.ok(Map.of(
                "totalDrivers", totalDrivers,
                "pendingKyc", pendingKyc,
                "activeOrders", activeOrders
        ));
    }

    @GetMapping("/drivers")
    public ResponseEntity<?> getDrivers(@RequestParam(required = false) String status) {
        List<Driver> drivers = driverRepository.findAll();
        
        if (status != null && !status.isEmpty()) {
            drivers = drivers.stream()
                    .filter(d -> status.equalsIgnoreCase(d.getKyc()))
                    .collect(Collectors.toList());
        }
        
        List<Map<String, Object>> response = drivers.stream().map(d -> Map.<String, Object>of(
                "driverId", d.getId().toString(),
                "name", d.getName() != null ? d.getName() : "Unknown",
                "kycStatus", d.getKyc() != null ? d.getKyc() : "pending"
        )).collect(Collectors.toList());
        
        return ResponseEntity.ok(response);
    }

    @PutMapping("/drivers/{driverId}/kyc")
    public ResponseEntity<?> updateDriverKyc(@PathVariable Long driverId, @RequestBody Map<String, String> payload) {
        Optional<Driver> driverOpt = driverRepository.findById(driverId);
        if (driverOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        Driver driver = driverOpt.get();
        String status = payload.get("status");
        if (status != null) {
            driver.setKyc(status);
            // If rejected, there could be a rejectedReason in the actual DB model, but for now we set the status.
            driverRepository.save(driver);
        }

        return ResponseEntity.ok(Map.of("success", true, "driverId", driver.getId().toString(), "kycStatus", driver.getKyc()));
    }
}
