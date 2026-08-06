package com.anushaporter.backend.controller;

import com.anushaporter.backend.model.AppUser;
import com.anushaporter.backend.model.Driver;
import com.anushaporter.backend.model.Order;
import com.anushaporter.backend.repository.AppUserRepository;
import com.anushaporter.backend.repository.DriverRepository;
import com.anushaporter.backend.repository.OrderRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class DriverAPIController {

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private OrderRepository orderRepository;

    private Driver getAuthenticatedDriver(HttpServletRequest request) {
        String email = (String) request.getAttribute("userId");
        Optional<AppUser> userOpt = appUserRepository.findFirstByEmailOrderByIdDesc(email);
        if (userOpt.isPresent()) {
            return driverRepository.findByPhone(userOpt.get().getPhone()).orElse(null);
        }
        return null;
    }

    private AppUser getAuthenticatedAppUser(HttpServletRequest request) {
        String email = (String) request.getAttribute("userId");
        return appUserRepository.findFirstByEmailOrderByIdDesc(email).orElse(null);
    }

    // A. Upload Documents
    @PostMapping("/upload")
    public ResponseEntity<?> uploadDocument(@RequestParam("file") MultipartFile file) {
        try {
            // Check if file is empty
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "File is empty"));
            }
            
            // Set up local storage path
            String uploadDir = "uploads/";
            File directory = new File(uploadDir);
            if (!directory.exists()) {
                directory.mkdirs();
            }

            // Generate unique filename
            String originalFileName = file.getOriginalFilename();
            String extension = originalFileName != null && originalFileName.contains(".") 
                ? originalFileName.substring(originalFileName.lastIndexOf(".")) 
                : ".jpg";
            String newFileName = UUID.randomUUID().toString() + extension;
            
            // Save file
            Path path = Paths.get(uploadDir + newFileName);
            Files.write(path, file.getBytes());

            // Return URL (In production this would be a full URL like https://your-server.com/uploads/filename)
            return ResponseEntity.ok(Map.of("url", "/uploads/" + newFileName));

        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", "Failed to upload file"));
        }
    }

    // B. Submit Registration
    @PostMapping("/drivers/register")
    public ResponseEntity<?> registerDriver(HttpServletRequest request, @RequestBody Map<String, Object> payload) {
        AppUser appUser = getAuthenticatedAppUser(request);
        if (appUser == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        String phone = appUser.getPhone();
        Driver driver = driverRepository.findByPhone(phone).orElse(new Driver());

        driver.setPhone(phone);
        driver.setEmail(appUser.getEmail());
        driver.setName((String) payload.get("name"));
        driver.setDob((String) payload.get("dob"));
        driver.setGender((String) payload.get("gender"));
        driver.setVehicleType((String) payload.get("vehicleType"));
        driver.setVehicleNumber((String) payload.get("vehicleNumber"));
        driver.setAadhaarNumber((String) payload.get("aadhaarNumber"));
        driver.setRcNumber((String) payload.get("rcNumber"));
        driver.setLicenseNumber((String) payload.get("licenseNumber"));
        driver.setBankName((String) payload.get("bankName"));
        driver.setAccountNumber((String) payload.get("accountNumber"));
        driver.setIfscCode((String) payload.get("ifscCode"));
        driver.setKyc("pending");
        driver.setStatus("offline"); // initial status

        @SuppressWarnings("unchecked")
        Map<String, String> docs = (Map<String, String>) payload.get("documents");
        if (docs != null) {
            driver.setProfilePhotoUri(docs.get("profilePhotoUrl"));
            driver.setAadhaarUri(docs.get("aadhaarUrl"));
            driver.setLicenseUri(docs.get("licenseUrl"));
            driver.setRcUri(docs.get("rcUrl"));
        }

        Driver saved = driverRepository.save(driver);

        return ResponseEntity.ok(Map.of(
            "success", true,
            "driverId", saved.getId().toString(),
            "kycStatus", saved.getKyc()
        ));
    }

    // Toggle Status
    @PutMapping("/drivers/me/status")
    public ResponseEntity<?> updateStatus(HttpServletRequest request, @RequestBody Map<String, String> payload) {
        Driver driver = getAuthenticatedDriver(request);
        if (driver == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized or Driver profile not found"));
        }

        String newStatus = payload.get("status");
        if (newStatus != null) {
            driver.setStatus(newStatus);
            driverRepository.save(driver);
        }

        return ResponseEntity.ok(Map.of("success", true, "status", driver.getStatus()));
    }

    // Fetch Earnings
    @GetMapping("/drivers/me/earnings")
    public ResponseEntity<?> getEarnings(HttpServletRequest request, @RequestParam(defaultValue = "today") String filter) {
        Driver driver = getAuthenticatedDriver(request);
        if (driver == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized or Driver profile not found"));
        }

        // Dummy calculations since actual payout entities are complex.
        // We will fetch orders for this driver and sum the driverEarning field (which maps to amount for now).
        // Let's assume all orders where driverId = driver.getId()
        
        List<Order> orders = orderRepository.findAll().stream()
                .filter(o -> o.getDriverId() != null && o.getDriverId().equals(driver.getId().toString()))
                .filter(o -> "completed".equalsIgnoreCase(o.getStatus()))
                .collect(Collectors.toList());

        double totalEarnings = orders.stream()
                .mapToDouble(o -> o.getAmount() != null ? o.getAmount() : 0.0)
                .sum();
        
        AppUser appUser = appUserRepository.findFirstByPhoneOrderByIdDesc(driver.getPhone()).orElse(null);
        double walletBalance = appUser != null && appUser.getWalletBalance() != null ? appUser.getWalletBalance() : 0.0;

        return ResponseEntity.ok(Map.of(
            "totalEarnings", totalEarnings,
            "tripsCompleted", orders.size(),
            "walletBalance", walletBalance
        ));
    }

    // Fetch History
    @GetMapping("/drivers/me/orders")
    public ResponseEntity<?> getOrderHistory(HttpServletRequest request) {
        Driver driver = getAuthenticatedDriver(request);
        if (driver == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized or Driver profile not found"));
        }

        List<Order> orders = orderRepository.findAll().stream()
                .filter(o -> o.getDriverId() != null && o.getDriverId().equals(driver.getId().toString()))
                .collect(Collectors.toList());

        List<Map<String, Object>> response = orders.stream().map(o -> {
            Map<String, Object> map = new HashMap<>();
            map.put("orderId", o.getBookingId() != null ? o.getBookingId() : o.getId().toString());
            map.put("status", o.getStatus());
            map.put("fare", o.getAmount());
            map.put("pickup", o.getPickupAddress());
            map.put("dropoff", o.getDropAddress());
            map.put("createdAt", o.getCreatedAt());
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }
}
