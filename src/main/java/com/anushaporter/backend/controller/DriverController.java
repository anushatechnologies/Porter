package com.anushaporter.backend.controller;

import com.anushaporter.backend.model.Driver;
import com.anushaporter.backend.repository.DriverRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.anushaporter.backend.model.Order;
import com.anushaporter.backend.repository.OrderRepository;
import org.springframework.http.HttpStatus;
import java.util.*;
import java.util.stream.Collectors;

import com.anushaporter.backend.model.Vehicle;
import com.anushaporter.backend.repository.VehicleRepository;

@RestController
@RequestMapping("/api/drivers")
public class DriverController {
    @Autowired
    private DriverRepository repository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private com.anushaporter.backend.repository.NotificationRepository notificationRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @GetMapping("/{email}/orders/active")
    public ResponseEntity<?> getActiveOrder(@PathVariable String email) {
        List<String> activeStatuses = Arrays.asList("assigned", "accepted", "picked_up", "transit");
        List<Order> orders = orderRepository.findAllByDriverEmailAndStatusInOrderByCreatedAtDesc(email, activeStatuses);
        if (!orders.isEmpty()) {
            return ResponseEntity.ok(orders.get(0));
        }
        return ResponseEntity.ok(java.util.Map.of());
    }

    @GetMapping("/{email}/orders/history")
    public List<Order> getOrderHistory(@PathVariable String email) {
        return orderRepository.findAllByDriverEmailOrderByCreatedAtDesc(email);
    }

    /**
     * GET /api/drivers
     * Returns formatted list of drivers for Admin Drivers roster & Live Driver GPS tracking map.
     */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAll() {
        List<Driver> drivers = repository.findAll();

        List<Map<String, Object>> items = drivers.stream().map(d -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", d.getId() != null ? "DRV-" + d.getId() : "DRV-100");
            map.put("driverId", d.getId() != null ? d.getId().toString() : "100");
            map.put("name", d.getName() != null ? d.getName() : "Unknown");
            map.put("email", d.getEmail() != null ? d.getEmail() : "");
            map.put("phone", d.getPhone() != null ? d.getPhone() : "");
            map.put("vehicleNumber", d.getVehicleNumber() != null ? d.getVehicleNumber() : "");
            map.put("status", d.getStatus() != null ? d.getStatus() : "online");
            map.put("kyc", d.getKyc() != null ? d.getKyc() : "pending");
            map.put("kycStatus", d.getKyc() != null ? d.getKyc() : "pending");
            map.put("rating", 4.8);
            map.put("licenseUri", d.getLicenseUri() != null ? d.getLicenseUri() : "");
            map.put("rcUri", d.getRcUri() != null ? d.getRcUri() : "");

            double lat = d.getLatitude() != null ? d.getLatitude() : 17.4483;
            double lng = d.getLongitude() != null ? d.getLongitude() : 78.3915;
            double speed = d.getSpeed() != null ? d.getSpeed() : 0.0;
            double angle = d.getHeading() != null ? d.getHeading() : 45.0;

            Map<String, Object> locMap = new LinkedHashMap<>();
            locMap.put("x", lat);
            locMap.put("y", lng);
            locMap.put("lat", lat);
            locMap.put("lng", lng);
            locMap.put("speed", speed);
            locMap.put("angle", angle);
            map.put("location", locMap);

            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(items);
    }

    @PostMapping
    public Driver create(@RequestBody Driver entity) {
        Driver savedDriver = repository.save(entity);

        if (entity.getVehicleNumber() != null && !entity.getVehicleNumber().trim().isEmpty()) {
            vehicleRepository.findByPlate(entity.getVehicleNumber()).ifPresentOrElse(veh -> {
                veh.setOwner(entity.getName());
                veh.setType(entity.getVehicleType());
                vehicleRepository.save(veh);
            }, () -> {
                Vehicle newVeh = new Vehicle();
                newVeh.setModel(entity.getVehicleType() + " Model");
                newVeh.setPlate(entity.getVehicleNumber());
                newVeh.setOwner(entity.getName());
                newVeh.setType(entity.getVehicleType());
                newVeh.setTrips(0);
                newVeh.setCapacity("500 kg");
                vehicleRepository.save(newVeh);
            });
        }
        return savedDriver;
    }

    @GetMapping("/email/{email}")
    public ResponseEntity<Driver> getByEmail(@PathVariable String email) {
        return repository.findByEmail(email)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @PutMapping("/email/{email}/status")
    public ResponseEntity<Driver> updateStatusByEmail(@PathVariable String email, @RequestBody java.util.Map<String, String> payload) {
        return repository.findByEmail(email).map(driver -> {
            driver.setStatus(payload.get("status"));
            return ResponseEntity.ok(repository.save(driver));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/verify")
    public ResponseEntity<java.util.Map<String, Object>> verifyDriver(@PathVariable Long id) {
        return repository.findById(id).map(driver -> {
            driver.setKyc("verified");

            com.anushaporter.backend.model.Notification notif = new com.anushaporter.backend.model.Notification();
            notif.setTitle("Account Approved!");
            notif.setMessage("Congratulations! Your partner account has been approved. You can now log in and accept orders.");
            notif.setAudience("driver");
            notif.setTarget(driver.getEmail());
            notif.setReadStatus(false);
            notificationRepository.save(notif);

            Driver savedDriver = repository.save(driver);
            return ResponseEntity.ok(java.util.Map.of("success", (Object) true, "driver", (Object) savedDriver));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<java.util.Map<String, Object>> rejectDriver(@PathVariable Long id) {
        return repository.findById(id).map(driver -> {
            driver.setKyc("rejected");

            com.anushaporter.backend.model.Notification notif = new com.anushaporter.backend.model.Notification();
            notif.setTitle("Verification Rejected");
            notif.setMessage("Your verification documents were rejected. Please review and update your documents.");
            notif.setAudience("driver");
            notif.setTarget(driver.getEmail());
            notif.setReadStatus(false);
            notificationRepository.save(notif);

            Driver savedDriver = repository.save(driver);
            return ResponseEntity.ok(java.util.Map.of("success", (Object) true, "driver", (Object) savedDriver));
        }).orElse(ResponseEntity.notFound().build());
    }
}
