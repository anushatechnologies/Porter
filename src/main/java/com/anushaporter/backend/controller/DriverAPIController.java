package com.anushaporter.backend.controller;

import com.anushaporter.backend.model.AppUser;
import com.anushaporter.backend.model.Driver;
import com.anushaporter.backend.model.Order;
import com.anushaporter.backend.repository.AppUserRepository;
import com.anushaporter.backend.repository.DriverRepository;
import com.anushaporter.backend.repository.OrderRepository;
import com.anushaporter.backend.repository.CustomerRepository;
import com.anushaporter.backend.service.PushNotificationService;
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

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private PushNotificationService pushNotificationService;

    @Autowired
    private com.anushaporter.backend.service.DriverAuthService driverAuthService;

    private Driver getAuthenticatedDriver(HttpServletRequest request) {
        return driverAuthService.resolveAuthenticatedDriver(request);
    }

    private AppUser getAuthenticatedAppUser(HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        if (userId == null) return null;
        Optional<AppUser> userOpt = appUserRepository.findFirstByEmailOrderByIdDesc(userId);
        if (userOpt.isPresent()) return userOpt.get();
        String cleanPhone = driverAuthService.normalizePhone(userId);
        if (!cleanPhone.isEmpty()) {
            userOpt = appUserRepository.findFirstByPhoneOrderByIdDesc(cleanPhone);
            if (userOpt.isPresent()) return userOpt.get();
        }
        return null;
    }

    @Autowired
    private com.anushaporter.backend.service.StorageService storageService;

    @GetMapping({"/drivers/me", "/driver/me"})
    public ResponseEntity<?> getDriverProfile(HttpServletRequest request) {
        Driver driver = getAuthenticatedDriver(request);
        if (driver == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "Unauthorized or Driver profile not found"));
        }
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("success", true);
        map.put("id", driver.getId());
        map.put("driverId", driver.getId() != null ? driver.getId().toString() : "");
        map.put("name", driver.getName() != null ? driver.getName() : "Driver");
        map.put("phone", driver.getPhone() != null ? driver.getPhone() : "");
        map.put("email", driver.getEmail() != null ? driver.getEmail() : "");
        map.put("status", driver.getStatus() != null ? driver.getStatus().toLowerCase() : "offline");
        map.put("kyc", driver.getKyc() != null ? driver.getKyc() : "pending");
        map.put("kycStatus", driver.getKyc() != null ? driver.getKyc() : "pending");
        map.put("rating", driver.getRating() != null ? driver.getRating() : "4.8");
        map.put("vehicle", driver.getVehicle() != null ? driver.getVehicle() : "");
        map.put("vehicleType", driver.getVehicleType() != null ? driver.getVehicleType() : "");
        map.put("vehicleNumber", driver.getVehicleNumber() != null ? driver.getVehicleNumber() : "");
        map.put("rcNumber", driver.getRcNumber() != null ? driver.getRcNumber() : "");
        map.put("licenseNumber", driver.getLicenseNumber() != null ? driver.getLicenseNumber() : "");
        map.put("aadhaarNumber", driver.getAadhaarNumber() != null ? driver.getAadhaarNumber() : "");
        map.put("trips", driver.getTrips() != null ? driver.getTrips() : 0);
        map.put("latitude", driver.getLatitude());
        map.put("longitude", driver.getLongitude());
        map.put("heading", driver.getHeading());
        map.put("speed", driver.getSpeed());
        map.put("location", driver.getLocation());
        map.put("profilePhotoUri", storageService.getPresignedOrSanitizedUrl(driver.getProfilePhotoUri()));
        map.put("licenseUri", storageService.getPresignedOrSanitizedUrl(driver.getLicenseUri()));
        map.put("rcUri", storageService.getPresignedOrSanitizedUrl(driver.getRcUri()));
        map.put("aadhaarUri", storageService.getPresignedOrSanitizedUrl(driver.getAadhaarUri()));
        map.put("bankPassbookUri", storageService.getPresignedOrSanitizedUrl(driver.getBankPassbookUri()));
        return ResponseEntity.ok(map);
    }

    @PostMapping("/drivers/me/device-token")
    public ResponseEntity<?> registerDeviceToken(HttpServletRequest request, @RequestBody Map<String, String> payload) {
        AppUser appUser = getAuthenticatedAppUser(request);
        Driver driver = getAuthenticatedDriver(request);
        if (appUser == null || driver == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "Driver profile not found"));
        }
        String fcmToken = payload.get("fcmToken");
        if (fcmToken == null || fcmToken.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "fcmToken is required"));
        }
        appUser.setFcmToken(fcmToken.trim());
        appUserRepository.save(appUser);
        return ResponseEntity.ok(Map.of("success", true, "message", "Device token registered"));
    }

    @PutMapping("/drivers/me/location")
    public ResponseEntity<?> updateLocation(HttpServletRequest request, @RequestBody Map<String, Object> payload) {
        Driver driver = getAuthenticatedDriver(request);
        if (driver == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "Driver profile not found"));
        }
        Number latitude = (Number) payload.get("latitude");
        Number longitude = (Number) payload.get("longitude");
        if (latitude == null || longitude == null
                || latitude.doubleValue() < -90 || latitude.doubleValue() > 90
                || longitude.doubleValue() < -180 || longitude.doubleValue() > 180) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Valid latitude and longitude are required"));
        }
        driver.setLatitude(latitude.doubleValue());
        driver.setLongitude(longitude.doubleValue());
        Number heading = (Number) payload.get("heading");
        if (heading != null) driver.setHeading(heading.doubleValue());
        driver.setLocation(latitude.doubleValue() + "," + longitude.doubleValue());
        driverRepository.save(driver);
        return ResponseEntity.ok(Map.of("success", true, "latitude", driver.getLatitude(),
                "longitude", driver.getLongitude(), "heading", driver.getHeading() == null ? 0 : driver.getHeading()));
    }

    @GetMapping("/drivers/me/orders/active")
    public ResponseEntity<?> getActiveOrder(HttpServletRequest request) {
        Driver driver = getAuthenticatedDriver(request);
        if (driver == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "Driver profile not found"));
        }
        List<String> activeStatuses = List.of("assigned", "accepted", "driver_assigned", "arriving_at_pickup",
                "pickup_started", "picked_up", "transit", "in_transit");
        List<Order> orders = orderRepository.findAllByDriverEmailAndStatusInOrderByCreatedAtDesc(driver.getEmail(), activeStatuses);
        if (orders.isEmpty()) {
            Map<String, Object> emptyResponse = new HashMap<>();
            emptyResponse.put("success", true);
            emptyResponse.put("order", null);
            return ResponseEntity.ok(emptyResponse);
        }

        Order order = orders.get(0);
        AppUser customer = appUserRepository.findFirstByEmailOrderByIdDesc(order.getUserEmail()).orElse(null);
        String customerName = customer != null ? customer.getName() : order.getReceiverName();
        String customerPhone = customer != null ? customer.getPhone() : order.getReceiverPhone();
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("orderId", order.getId());
        result.put("bookingId", order.getBookingId());
        result.put("status", order.getStatus());
        result.put("customerName", customerName);
        result.put("customerPhone", customerPhone);
        result.put("pickup", coordinateOrAddress(order.getPickupLat(), order.getPickupLng(), order.getPickupAddress()));
        result.put("drop", coordinateOrAddress(order.getDropLat(), order.getDropLng(), order.getDropAddress()));
        result.put("pickupAddress", order.getPickupAddress());
        result.put("dropAddress", order.getDropAddress());
        result.put("amount", order.getAmount());
        return ResponseEntity.ok(Map.of("success", true, "order", result));
    }

    private String coordinateOrAddress(Double latitude, Double longitude, String address) {
        if (latitude != null && longitude != null) return latitude + ", " + longitude;
        return address == null ? "" : address;
    }

    @PutMapping("/driver/location")
    public ResponseEntity<?> updateLocationAlias(HttpServletRequest request, @RequestBody Map<String, Object> payload) {
        if (payload.containsKey("lat")) payload.put("latitude", payload.get("lat"));
        if (payload.containsKey("lng")) payload.put("longitude", payload.get("lng"));
        return updateLocation(request, payload);
    }

    @RequestMapping(value = {"/driver/orders/{bookingId}/accept", "/drivers/orders/{bookingId}/accept"}, method = {RequestMethod.PUT, RequestMethod.POST})
    public ResponseEntity<?> acceptOrderByBookingId(HttpServletRequest request, @PathVariable String bookingId) {
        Driver driver = getAuthenticatedDriver(request);
        if (driver == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "Driver profile not found or unauthorized"));
        }

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
        String currentStatus = order.getStatus() != null ? order.getStatus().toLowerCase() : "searching";
        boolean isClaimable = currentStatus.equals("searching") || currentStatus.equals("pending");

        boolean isSameDriver = (driver.getId() != null && driver.getId().toString().equals(order.getDriverId()))
                || (driver.getEmail() != null && driver.getEmail().equalsIgnoreCase(order.getDriverEmail()));

        if (!isClaimable && !isSameDriver) {
            return ResponseEntity.status(409).body(Map.of(
                    "success", false,
                    "message", "This order has already been accepted by another driver."
            ));
        }

        int rows = orderRepository.claimOrderByIdAtomic(
                order.getId(),
                driver.getId() != null ? driver.getId().toString() : "",
                driver.getName(),
                driver.getEmail(),
                driver.getPhone(),
                driver.getVehicleNumber()
        );

        if (rows == 0 && !isSameDriver) {
            return ResponseEntity.status(409).body(Map.of(
                    "success", false,
                    "message", "This order has already been accepted by another driver."
            ));
        }

        order.setDriverId(driver.getId() != null ? driver.getId().toString() : "");
        order.setDriverName(driver.getName());
        order.setDriverEmail(driver.getEmail());
        order.setDriverPhone(driver.getPhone());
        order.setDriverVehicleNumber(driver.getVehicleNumber());
        order.setStatus("accepted");

        Order saved = orderRepository.findById(order.getId()).orElse(order);
        pushNotificationService.notifyOrderStatus(saved, saved.getStatus());
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Order accepted successfully",
                "bookingId", saved.getBookingId() != null ? saved.getBookingId() : bookingId,
                "status", "accepted",
                "order", saved
        ));
    }

    @PostMapping("/driver/orders/{bookingId}/verify-otp")
    public ResponseEntity<?> verifyDeliveryOtp(HttpServletRequest request, @PathVariable String bookingId,
                                               @RequestBody Map<String, String> payload) {
        Driver driver = getAuthenticatedDriver(request); Order order = orderRepository.findByBookingId(bookingId).orElse(null);
        String otp = payload.get("otp");
        if (driver == null) return ResponseEntity.status(401).body(Map.of("success", false, "message", "Driver profile not found"));
        if (order == null || otp == null || !otp.equals(order.getDeliveryOtp()) || order.getOtpExpiresAt() == null
                || order.getOtpExpiresAt().isBefore(java.time.LocalDateTime.now()))
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Invalid or expired delivery OTP"));
        order.setStatus("delivered"); orderRepository.save(order); pushNotificationService.notifyOrderStatus(order, order.getStatus());
        return ResponseEntity.ok(Map.of("success", true, "bookingId", bookingId, "status", "delivered"));
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

        String aadhaar = text(payload, "aadhaarNumber");
        String pincode = text(payload, "pincode");
        String ifsc = text(payload, "ifscCode");
        if (aadhaar != null && !aadhaar.matches("\\d{12}")) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "aadhaarNumber must contain exactly 12 digits"));
        }
        if (pincode != null && !pincode.matches("\\d{6}")) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "pincode must contain exactly 6 digits"));
        }
        if (ifsc != null && !ifsc.matches("[A-Za-z0-9]{11}")) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "ifscCode must contain exactly 11 letters or digits"));
        }

        driver.setPhone(phone);
        driver.setEmail(text(payload, "email") != null ? text(payload, "email") : appUser.getEmail());
        driver.setName(text(payload, "name"));
        driver.setDob(text(payload, "dob"));
        driver.setGender(text(payload, "gender"));
        driver.setVehicleType(text(payload, "vehicleType"));
        driver.setVehicleNumber(text(payload, "vehicleNumber"));
        driver.setAadhaarNumber(aadhaar);
        driver.setRcNumber(text(payload, "rcNumber"));
        driver.setLicenseNumber(text(payload, "licenseNumber"));
        driver.setAddressLine1(text(payload, "addressLine1"));
        driver.setCity(text(payload, "city"));
        driver.setState(text(payload, "state"));
        driver.setPincode(pincode);
        driver.setBankName(text(payload, "bankName"));
        driver.setAccountHolderName(text(payload, "accountHolderName"));
        driver.setAccountNumber(text(payload, "accountNumber"));
        driver.setIfscCode(ifsc);
        driver.setKyc("pending");
        driver.setStatus("offline"); // initial status

        @SuppressWarnings("unchecked")
        Map<String, String> docs = (Map<String, String>) payload.get("documents");
        if (docs != null) {
            driver.setProfilePhotoUri(storageService.sanitizeUri(docs.get("profilePhotoUrl")));
            driver.setAadhaarUri(storageService.sanitizeUri(docs.get("aadhaarUrl")));
            driver.setLicenseUri(storageService.sanitizeUri(docs.get("licenseUrl")));
            driver.setRcUri(storageService.sanitizeUri(docs.get("rcUrl")));
            driver.setBankPassbookUri(storageService.sanitizeUri(docs.get("bankPassbookUrl")));
        }

        Driver saved = driverRepository.save(driver);

        return ResponseEntity.ok(Map.of(
            "success", true,
            "driverId", saved.getId().toString(),
            "kycStatus", saved.getKyc()
        ));
    }

    private String text(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null) return null;
        String result = String.valueOf(value).trim();
        return result.isEmpty() ? null : result;
    }

    // Toggle Status
    @RequestMapping(value = {"/drivers/me/status", "/driver/me/status", "/drivers/status", "/driver/status"}, method = {RequestMethod.PUT, RequestMethod.POST, RequestMethod.PATCH})
    public ResponseEntity<?> updateStatus(HttpServletRequest request, @RequestBody(required = false) Map<String, Object> payload) {
        Driver driver = getAuthenticatedDriver(request);
        if (driver == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "error", "Unauthorized or Driver profile not found"));
        }

        Object rawStatus = null;
        if (payload != null) {
            rawStatus = payload.get("status");
            if (rawStatus == null) rawStatus = payload.get("online");
            if (rawStatus == null) rawStatus = payload.get("isOnline");
        }

        String newStatus = driverAuthService.normalizeStatus(rawStatus);
        driver.setStatus(newStatus);
        Driver saved = driverRepository.save(driver);

        return ResponseEntity.ok(Map.of(
            "success", true,
            "status", saved.getStatus() != null ? saved.getStatus().toLowerCase() : newStatus,
            "driver", saved
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
