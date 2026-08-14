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

import com.anushaporter.backend.model.AppUser;
import com.anushaporter.backend.repository.AppUserRepository;
import com.anushaporter.backend.model.Vehicle;
import com.anushaporter.backend.repository.VehicleRepository;

@RestController
@RequestMapping("/api/drivers")
public class DriverController {
    @Autowired
    private DriverRepository repository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired(required = false)
    private AppUserRepository appUserRepository;

    @Autowired
    private com.anushaporter.backend.repository.NotificationRepository notificationRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private com.anushaporter.backend.service.DriverAuthService driverAuthService;

    @GetMapping({"/me/orders/active", "/active-order", "/{email}/orders/active"})
    public ResponseEntity<?> getActiveOrder(@PathVariable(required = false) String email) {
        List<String> activeStatuses = Arrays.asList("assigned", "accepted", "picked_up", "transit", "driver_assigned", "in_transit");
        List<Order> orders;

        if (email != null && !email.isBlank() && !email.equalsIgnoreCase("me")) {
            orders = orderRepository.findAllByDriverEmailAndStatusInOrderByCreatedAtDesc(email, activeStatuses);
        } else {
            orders = orderRepository.findAll().stream()
                    .filter(o -> o.getStatus() != null && activeStatuses.contains(o.getStatus().toLowerCase()))
                    .sorted((o1, o2) -> o2.getId().compareTo(o1.getId()))
                    .collect(Collectors.toList());
        }

        if (!orders.isEmpty()) {
            Order o = orders.get(0);
            if (o.getDeliveryOtp() == null || o.getDeliveryOtp().isBlank()) {
                o.setDeliveryOtp("8813");
                orderRepository.save(o);
            }

            String userEmail = o.getUserEmail() != null ? o.getUserEmail() : "";
            AppUser user = (appUserRepository != null && !userEmail.isBlank())
                    ? appUserRepository.findFirstByEmailOrderByIdDesc(userEmail).orElse(null)
                    : null;

            String custName = (user != null && user.getName() != null && !user.getName().isBlank())
                    ? user.getName()
                    : (o.getReceiverName() != null && !o.getReceiverName().isBlank() ? o.getReceiverName() : "Customer Name Here");

            String custPhone = (user != null && user.getPhone() != null && !user.getPhone().isBlank())
                    ? user.getPhone()
                    : (o.getReceiverPhone() != null && !o.getReceiverPhone().isBlank() ? o.getReceiverPhone() : "9876543210");

            Map<String, Object> orderMap = new LinkedHashMap<>();
            orderMap.put("id", o.getBookingId() != null ? o.getBookingId() : "BK_" + o.getId());
            orderMap.put("bookingId", o.getBookingId() != null ? o.getBookingId() : "BK_" + o.getId());
            orderMap.put("status", o.getStatus());
            orderMap.put("amount", o.getAmount() != null ? o.getAmount() : 0.0);
            orderMap.put("customerName", custName);
            orderMap.put("customerPhone", custPhone);
            orderMap.put("customer_name", custName);
            orderMap.put("customer_phone", custPhone);
            orderMap.put("receiverName", o.getReceiverName() != null ? o.getReceiverName() : custName);
            orderMap.put("receiverPhone", o.getReceiverPhone() != null ? o.getReceiverPhone() : custPhone);
            orderMap.put("senderName", custName);
            orderMap.put("senderPhone", custPhone);
            orderMap.put("contactName", custName);
            orderMap.put("contactPhone", custPhone);
            orderMap.put("pickupAddress", o.getPickupAddress() != null ? o.getPickupAddress() : "");
            orderMap.put("dropAddress", o.getDropAddress() != null ? o.getDropAddress() : "");
            orderMap.put("deliveryOtp", o.getDeliveryOtp() != null ? o.getDeliveryOtp() : "8813");

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("order", orderMap);
            return ResponseEntity.ok(response);
        }

        return ResponseEntity.ok(Map.of("success", true, "order", null));
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
            map.put("status", d.getStatus() != null ? d.getStatus().toLowerCase() : "offline");
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
        if (entity.getStatus() == null || entity.getStatus().isBlank()) {
            entity.setStatus("offline");
        } else {
            entity.setStatus(driverAuthService.normalizeStatus(entity.getStatus()));
        }
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
    public ResponseEntity<?> getByEmail(@PathVariable String email) {
        Driver driver = driverAuthService.resolveDriverByIdentifier(email);
        if (driver == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(driver);
    }

    @PutMapping("/email/{email}/status")
    public ResponseEntity<?> updateStatusByEmail(@PathVariable String email, @RequestBody(required = false) Map<String, Object> payload) {
        Driver driver = driverAuthService.resolveDriverByIdentifier(email);
        if (driver == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("success", false, "message", "Driver not found with email: " + email));
        }

        Object rawStatus = null;
        if (payload != null) {
            rawStatus = payload.get("status");
            if (rawStatus == null) rawStatus = payload.get("online");
            if (rawStatus == null) rawStatus = payload.get("isOnline");
        }

        String newStatus = driverAuthService.normalizeStatus(rawStatus);
        driver.setStatus(newStatus);
        Driver saved = repository.save(driver);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("status", saved.getStatus() != null ? saved.getStatus().toLowerCase() : newStatus);
        response.put("driver", saved);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{driverId}/status")
    public ResponseEntity<?> updateStatusById(@PathVariable String driverId, jakarta.servlet.http.HttpServletRequest request, @RequestBody(required = false) Map<String, Object> payload) {
        Driver driver = null;
        if ("me".equalsIgnoreCase(driverId)) {
            driver = driverAuthService.resolveAuthenticatedDriver(request);
        } else {
            driver = driverAuthService.resolveDriverByIdentifier(driverId);
        }

        if (driver == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("success", false, "message", "Driver not found: " + driverId));
        }

        Object rawStatus = null;
        if (payload != null) {
            rawStatus = payload.get("status");
            if (rawStatus == null) rawStatus = payload.get("online");
            if (rawStatus == null) rawStatus = payload.get("isOnline");
        }

        String newStatus = driverAuthService.normalizeStatus(rawStatus);
        driver.setStatus(newStatus);
        Driver saved = repository.save(driver);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("status", saved.getStatus() != null ? saved.getStatus().toLowerCase() : newStatus);
        return ResponseEntity.ok(response);
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
