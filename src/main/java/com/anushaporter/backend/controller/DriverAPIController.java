package com.anushaporter.backend.controller;

import com.anushaporter.backend.model.AppUser;
import com.anushaporter.backend.model.Driver;
import com.anushaporter.backend.model.Order;
import com.anushaporter.backend.model.VehicleType;
import com.anushaporter.backend.repository.AppUserRepository;
import com.anushaporter.backend.repository.DriverRepository;
import com.anushaporter.backend.repository.OrderRepository;
import com.anushaporter.backend.repository.CustomerRepository;
import com.anushaporter.backend.repository.VehicleTypeRepository;
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
import java.util.*;
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

    @Autowired(required = false)
    private VehicleTypeRepository vehicleTypeRepository;

    @Autowired
    private PushNotificationService pushNotificationService;

    @Autowired
    private com.anushaporter.backend.service.DriverAuthService driverAuthService;

    @Autowired
    private com.anushaporter.backend.service.DeliveryCompletionService deliveryCompletionService;

    public Driver getAuthenticatedDriver(HttpServletRequest request) {
        return driverAuthService.resolveAuthenticatedDriver(request);
    }

    private AppUser getAuthenticatedAppUser(HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        if (userId == null)
            return null;
        Optional<AppUser> userOpt = appUserRepository.findFirstByEmailOrderByIdDesc(userId);
        if (userOpt.isPresent())
            return userOpt.get();
        String cleanPhone = driverAuthService.normalizePhone(userId);
        if (!cleanPhone.isEmpty()) {
            userOpt = appUserRepository.findFirstByPhoneOrderByIdDesc(cleanPhone);
            if (userOpt.isPresent())
                return userOpt.get();
        }
        return null;
    }

    @Autowired
    private com.anushaporter.backend.service.StorageService storageService;

    @GetMapping({ "/drivers/me", "/driver/me" })
    public ResponseEntity<?> getDriverProfile(HttpServletRequest request) {
        Driver driver = getAuthenticatedDriver(request);
        if (driver == null) {
            return ResponseEntity.status(401)
                    .body(Map.of("success", false, "message", "Unauthorized or Driver profile not found"));
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
        String vType = driver.getVehicleType() != null && !driver.getVehicleType().isBlank() ? driver.getVehicleType()
                : (driver.getVehicle() != null && !driver.getVehicle().isBlank() ? driver.getVehicle() : "Vehicle");
        String v = driver.getVehicle() != null && !driver.getVehicle().isBlank() ? driver.getVehicle()
                : (driver.getVehicleType() != null && !driver.getVehicleType().isBlank() ? driver.getVehicleType()
                        : "Vehicle");
        map.put("vehicle", v);
        map.put("vehicleType", vType);
        map.put("vehicle_type", vType);
        map.put("vehicleName", vType);
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
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Valid latitude and longitude are required"));
        }
        driver.setLatitude(latitude.doubleValue());
        driver.setLongitude(longitude.doubleValue());
        Number heading = (Number) payload.get("heading");
        if (heading != null)
            driver.setHeading(heading.doubleValue());
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
        List<Order> orders = orderRepository.findAllByDriverEmailAndStatusInOrderByCreatedAtDesc(driver.getEmail(),
                activeStatuses);
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
        // Include distance and coordinates so the driver app uses the actual route
        // distance
        // instead of falling back to a fare-based estimate (which clamps to 1.0 km for
        // low fares)
        result.put("distance", order.getDistanceKm() != null
                ? String.format("%.1f", order.getDistanceKm())
                : null);
        result.put("distanceKm", order.getDistanceKm());
        result.put("pickupLat", order.getPickupLat());
        result.put("pickupLng", order.getPickupLng());
        result.put("dropLat", order.getDropLat());
        result.put("dropLng", order.getDropLng());
        return ResponseEntity.ok(Map.of("success", true, "order", result));
    }

    private String coordinateOrAddress(Double latitude, Double longitude, String address) {
        if (latitude != null && longitude != null)
            return latitude + ", " + longitude;
        return address == null ? "" : address;
    }

    @PutMapping("/driver/location")
    public ResponseEntity<?> updateLocationAlias(HttpServletRequest request, @RequestBody Map<String, Object> payload) {
        if (payload.containsKey("lat"))
            payload.put("latitude", payload.get("lat"));
        if (payload.containsKey("lng"))
            payload.put("longitude", payload.get("lng"));
        return updateLocation(request, payload);
    }

    @RequestMapping(value = { "/driver/orders/{bookingId}/accept", "/drivers/orders/{bookingId}/accept" }, method = {
            RequestMethod.PUT, RequestMethod.POST })
    public ResponseEntity<?> acceptOrderByBookingId(
            HttpServletRequest request,
            @PathVariable String bookingId,
            @RequestBody(required = false) Map<String, Object> payload) {
        Driver driver = getAuthenticatedDriver(request);

        String driverId = driver != null && driver.getId() != null ? driver.getId().toString()
                : (payload != null && payload.get("driverId") != null ? String.valueOf(payload.get("driverId")) : null);
        String driverName = driver != null ? driver.getName()
                : (payload != null && payload.get("driverName") != null ? String.valueOf(payload.get("driverName"))
                        : null);
        String driverEmail = driver != null ? driver.getEmail()
                : (payload != null && payload.get("driverEmail") != null ? String.valueOf(payload.get("driverEmail"))
                        : null);
        String driverPhone = driver != null ? driver.getPhone()
                : (payload != null && payload.get("driverPhone") != null ? String.valueOf(payload.get("driverPhone"))
                        : null);
        String driverVehicle = driver != null ? driver.getVehicleNumber()
                : (payload != null && payload.get("driverVehicleNumber") != null
                        ? String.valueOf(payload.get("driverVehicleNumber"))
                        : (payload != null && payload.get("vehicleNumber") != null
                                ? String.valueOf(payload.get("vehicleNumber"))
                                : null));

        if (driver == null && driverId == null && driverEmail == null) {
            Map<String, Object> unauth = new LinkedHashMap<>();
            unauth.put("success", false);
            unauth.put("statusCode", 401);
            unauth.put("message", "Driver profile not found or unauthorized");
            return ResponseEntity.status(401).body(unauth);
        }

        Optional<Order> orderOpt = orderRepository.findByBookingId(bookingId);
        if (orderOpt.isEmpty()) {
            try {
                orderOpt = orderRepository.findById(Long.valueOf(bookingId));
            } catch (NumberFormatException ignored) {
            }
        }
        if (orderOpt.isEmpty()) {
            Map<String, Object> notFound = new LinkedHashMap<>();
            notFound.put("success", false);
            notFound.put("statusCode", 404);
            notFound.put("message", "Order not found or has expired.");
            return ResponseEntity.status(404).body(notFound);
        }

        Order order = orderOpt.get();
        boolean isSameDriver = (driverId != null && order.getDriverId() != null
                && driverId.trim().equalsIgnoreCase(order.getDriverId().trim()))
                || (driverEmail != null && order.getDriverEmail() != null
                        && driverEmail.trim().equalsIgnoreCase(order.getDriverEmail().trim()))
                || (driverPhone != null && order.getDriverPhone() != null
                        && driverPhone.trim().equals(order.getDriverPhone().trim()));

        // If this same driver already claimed the order, return idempotent success
        if (isSameDriver) {
            Map<String, Object> idempotentSuccess = new LinkedHashMap<>();
            idempotentSuccess.put("success", true);
            idempotentSuccess.put("statusCode", 200);
            idempotentSuccess.put("message", "You have already accepted this order.");
            idempotentSuccess.put("bookingId", order.getBookingId() != null ? order.getBookingId() : bookingId);
            idempotentSuccess.put("status", "accepted");
            idempotentSuccess.put("order", order);
            return ResponseEntity.ok(idempotentSuccess);
        }

        // If order is not in a claimable status and not accepted by this driver -> 409
        // Conflict
        if (!isOrderClaimable(order.getStatus())) {
            Map<String, Object> conflict = new LinkedHashMap<>();
            conflict.put("success", false);
            conflict.put("statusCode", 409);
            conflict.put("message", "This order has already been accepted by another driver partner.");
            Map<String, Object> orderSummary = new LinkedHashMap<>();
            orderSummary.put("id", order.getId());
            if (order.getBookingId() != null)
                orderSummary.put("bookingId", order.getBookingId());
            orderSummary.put("status", order.getStatus() != null ? order.getStatus() : "accepted");
            conflict.put("order", orderSummary);
            return ResponseEntity.status(409).body(conflict);
        }

        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        int rows = orderRepository.claimOrderByIdAtomic(
                order.getId(),
                driverId != null ? driverId : "",
                driverName,
                driverEmail,
                driverPhone,
                driverVehicle,
                now);

        if (rows == 0) {
            Order fresh = orderRepository.findById(order.getId()).orElse(order);
            boolean freshIsSame = (driverId != null && driverId.equalsIgnoreCase(fresh.getDriverId()))
                    || (driverEmail != null && driverEmail.equalsIgnoreCase(fresh.getDriverEmail()))
                    || (driverPhone != null && driverPhone.equals(fresh.getDriverPhone()));

            if (freshIsSame) {
                Map<String, Object> idempotentSuccess = new LinkedHashMap<>();
                idempotentSuccess.put("success", true);
                idempotentSuccess.put("statusCode", 200);
                idempotentSuccess.put("message", "You have already accepted this order.");
                idempotentSuccess.put("bookingId", fresh.getBookingId() != null ? fresh.getBookingId() : bookingId);
                idempotentSuccess.put("status", "accepted");
                idempotentSuccess.put("order", fresh);
                return ResponseEntity.ok(idempotentSuccess);
            }

            Map<String, Object> conflict = new LinkedHashMap<>();
            conflict.put("success", false);
            conflict.put("statusCode", 409);
            conflict.put("message", "This order has already been accepted by another driver partner.");
            Map<String, Object> orderSummary = new LinkedHashMap<>();
            orderSummary.put("id", fresh.getId());
            if (fresh.getBookingId() != null)
                orderSummary.put("bookingId", fresh.getBookingId());
            orderSummary.put("status", fresh.getStatus() != null ? fresh.getStatus() : "accepted");
            conflict.put("order", orderSummary);
            return ResponseEntity.status(409).body(conflict);
        }

        order.setDriverId(driverId != null ? driverId : "");
        order.setDriverName(driverName);
        order.setDriverEmail(driverEmail);
        order.setDriverPhone(driverPhone);
        order.setDriverVehicleNumber(driverVehicle);
        order.setStatus("accepted");
        order.setAcceptedAt(now);

        Order saved = orderRepository.findById(order.getId()).orElse(order);
        if (pushNotificationService != null) {
            pushNotificationService.notifyOrderStatus(saved, saved.getStatus());
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("statusCode", 200);
        response.put("message", "Order accepted successfully");
        response.put("bookingId", saved.getBookingId() != null ? saved.getBookingId() : bookingId);
        response.put("status", "accepted");
        response.put("order", saved);
        return ResponseEntity.ok(response);
    }

    private boolean isOrderClaimable(String status) {
        if (status == null || status.isBlank())
            return true;
        String s = status.trim().toLowerCase();
        return s.equals("searching") || s.equals("pending") || s.equals("created")
                || s.equals("broadcasted") || s.equals("unassigned") || s.equals("placed")
                || s.equals("available");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // STEP 1 — POST /api/driver/orders/:bookingId/verify-otp
    //
    // Driver receives the 4-digit OTP from the customer and submits it here.
    // On success: status → payment_confirmation_pending, otpVerified = true.
    // The order is NOT yet marked as delivered/completed.
    // ─────────────────────────────────────────────────────────────────────────
    @PostMapping({
            "/driver/orders/{bookingId}/verify-otp",
            "/drivers/orders/{bookingId}/verify-otp"
    })
    public ResponseEntity<?> verifyDeliveryOtp(
            HttpServletRequest request,
            @PathVariable String bookingId,
            @RequestBody(required = false) Map<String, String> payload) {

        Driver driver = getAuthenticatedDriver(request);
        if (driver == null) {
            return ResponseEntity.status(401)
                    .body(Map.of("success", false, "message", "Unauthorized: driver profile not found"));
        }

        String inputOtp = null;
        if (payload != null) {
            inputOtp = payload.get("enteredOtp");
            if (inputOtp == null)
                inputOtp = payload.get("otp");
            if (inputOtp == null)
                inputOtp = payload.get("deliveryOtp");
        }

        Map<String, Object> result = deliveryCompletionService.verifyOtp(bookingId, inputOtp, driver);
        int httpStatus = result.containsKey("httpStatus") ? (int) result.get("httpStatus") : 200;
        result.remove("httpStatus");
        return ResponseEntity.status(httpStatus).body(result);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // STEP 2 — POST /api/driver/orders/:bookingId/confirm-payment
    //
    // Driver confirms collected payment (Cash / Online).
    // Pre-requisite: otpVerified must be true (Step 1 must have been done).
    // Validates amount, checks idempotency, calculates 5% commission, credits
    // wallet.
    // ─────────────────────────────────────────────────────────────────────────
    @PostMapping({
            "/driver/orders/{bookingId}/confirm-payment",
            "/drivers/orders/{bookingId}/confirm-payment",
            "/driver/orders/{bookingId}/complete",
            "/drivers/orders/{bookingId}/complete"
    })
    public ResponseEntity<?> confirmPayment(
            HttpServletRequest request,
            @PathVariable String bookingId,
            @RequestBody(required = false) Map<String, Object> payload) {

        Driver driver = getAuthenticatedDriver(request);
        if (driver == null) {
            return ResponseEntity.status(401)
                    .body(Map.of("success", false, "message", "Unauthorized: driver profile not found"));
        }

        String idempotencyKey = request.getHeader("Idempotency-Key");
        if (idempotencyKey == null)
            idempotencyKey = request.getHeader("idempotency-key");
        if (idempotencyKey == null)
            idempotencyKey = request.getHeader("X-Idempotency-Key");

        String paymentMethod = null;
        Double amount = null;
        if (payload != null) {
            paymentMethod = payload.get("paymentMethod") != null ? String.valueOf(payload.get("paymentMethod"))
                    : payload.get("method") != null ? String.valueOf(payload.get("method")) : null;

            Object rawAmt = payload.get("amount");
            if (rawAmt instanceof Number) {
                amount = ((Number) rawAmt).doubleValue();
            } else if (rawAmt != null) {
                try {
                    amount = Double.parseDouble(rawAmt.toString());
                } catch (NumberFormatException ignored) {
                }
            }

            if (idempotencyKey == null && payload.get("idempotencyKey") != null) {
                idempotencyKey = String.valueOf(payload.get("idempotencyKey"));
            }
        }

        Map<String, Object> result = deliveryCompletionService.confirmPaymentAndComplete(
                bookingId, paymentMethod, amount, idempotencyKey, driver);

        int httpStatus = result.containsKey("httpStatus") ? (int) result.get("httpStatus") : 200;
        result.remove("httpStatus");
        return ResponseEntity.status(httpStatus).body(result);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Alternative routes with bookingId / orderId in Request Body
    // ─────────────────────────────────────────────────────────────────────────
    @PostMapping({
            "/verify-otp",
            "/driver/verify-otp",
            "/orders/verify-otp"
    })
    public ResponseEntity<?> verifyDeliveryOtpFromBody(
            HttpServletRequest request,
            @RequestBody(required = false) Map<String, String> payload) {
        if (payload == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Request body is missing"));
        }
        String bookingId = payload.get("bookingId") != null ? payload.get("bookingId")
                : payload.get("orderId");
        if (bookingId == null || bookingId.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "bookingId or orderId is required"));
        }
        return verifyDeliveryOtp(request, bookingId, payload);
    }

    @PostMapping({
            "/confirm-payment",
            "/driver/confirm-payment",
            "/orders/confirm-payment"
    })
    public ResponseEntity<?> confirmPaymentFromBody(
            HttpServletRequest request,
            @RequestBody(required = false) Map<String, Object> payload) {
        if (payload == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Request body is missing"));
        }
        String bookingId = payload.get("bookingId") != null ? String.valueOf(payload.get("bookingId"))
                : payload.get("orderId") != null ? String.valueOf(payload.get("orderId")) : null;
        if (bookingId == null || bookingId.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "bookingId or orderId is required"));
        }
        return confirmPayment(request, bookingId, payload);
    }

    // A. Upload Documents
    @PostMapping({ "/upload", "/driver/documents/upload", "/drivers/documents/upload" })
    public ResponseEntity<?> uploadDocument(@RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "File is empty"));
            }

            String uploadDir = "uploads/";
            File directory = new File(uploadDir);
            if (!directory.exists()) {
                directory.mkdirs();
            }

            String originalFileName = file.getOriginalFilename();
            String extension = originalFileName != null && originalFileName.contains(".")
                    ? originalFileName.substring(originalFileName.lastIndexOf("."))
                    : ".jpg";
            String newFileName = UUID.randomUUID().toString() + extension;

            Path path = Paths.get(uploadDir + newFileName);
            Files.write(path, file.getBytes());

            return ResponseEntity.ok(Map.of("url", "/uploads/" + newFileName, "success", true));

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
            return ResponseEntity.status(401).body(Map.of("success", false, "error", "Unauthorized", "message",
                    "Your session has expired. Please login again."));
        }

        String phone = appUser.getPhone();
        Driver driver = driverRepository.findByPhone(phone).orElse(new Driver());

        // Check if KYC application already exists (HTTP 409)
        if (driver.getId() != null && driver.getKyc() != null &&
                ("pending".equalsIgnoreCase(driver.getKyc()) || "verified".equalsIgnoreCase(driver.getKyc())
                        || "approved".equalsIgnoreCase(driver.getKyc()))) {
            return ResponseEntity.status(409).body(
                    Map.of("success", false, "error", "Conflict", "message", "Your KYC application already exists."));
        }

        String name = text(payload, "name");
        String aadhaar = text(payload, "aadhaarNumber");
        String pincode = text(payload, "pincode");
        String ifsc = text(payload, "ifscCode");
        String accountNumber = text(payload, "accountNumber");
        String licenseNumber = text(payload, "licenseNumber");
        String rcNumber = text(payload, "rcNumber");

        // Strict Backend Validation Checks (HTTP 400)
        if (name != null && !name.matches("^[a-zA-Z\\s]{2,50}$")) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message",
                    "Name must contain only alphabets and spaces (2-50 characters)."));
        }
        if (aadhaar != null && !aadhaar.replaceAll("\\s+", "").matches("^\\d{12}$")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Aadhaar number must contain exactly 12 digits."));
        }
        if (pincode != null && !pincode.matches("^\\d{6}$")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Pincode must contain exactly 6 digits."));
        }
        if (ifsc != null && !ifsc.toUpperCase().matches("^[A-Z]{4}0[A-Z0-9]{6}$")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Invalid IFSC code format (e.g. HDFC0001234)."));
        }
        if (accountNumber != null && !accountNumber.matches("^\\d{9,18}$")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Bank Account number must contain 9 to 18 digits."));
        }

        // ── Vehicle type validation ──────────────────────────────────────────
        // If vehicleId is provided, verify it exists and is active in the DB
        String vehicleId = text(payload, "vehicleId");
        if (vehicleId != null && !vehicleId.isBlank() && vehicleTypeRepository != null) {
            boolean isValid = vehicleTypeRepository
                    .findByIdAndStatus(vehicleId, "active")
                    .isPresent();
            if (!isValid) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Selected vehicle type is no longer available."));
            }
        }

        // Resolve unified vehicle string
        String vehicleVal = text(payload, "vehicle");
        String vehicleTypeVal = text(payload, "vehicleType");
        String vehicle_typeVal = text(payload, "vehicle_type");
        String vehicleNameVal = text(payload, "vehicleName");
        String resolvedVehicle = vehicleVal != null ? vehicleVal
                : (vehicleTypeVal != null ? vehicleTypeVal
                        : (vehicle_typeVal != null ? vehicle_typeVal
                                : (vehicleNameVal != null ? vehicleNameVal : "Vehicle")));

        driver.setPhone(phone);
        driver.setEmail(text(payload, "email") != null ? text(payload, "email") : appUser.getEmail());
        driver.setName(name);
        driver.setDob(text(payload, "dob"));
        driver.setGender(text(payload, "gender"));
        driver.setVehicle(resolvedVehicle);
        driver.setVehicleType(resolvedVehicle);
        driver.setVehicleNumber(text(payload, "vehicleNumber"));
        driver.setAadhaarNumber(aadhaar);
        driver.setRcNumber(rcNumber);
        driver.setLicenseNumber(licenseNumber);
        driver.setAddressLine1(text(payload, "addressLine1"));
        driver.setCity(text(payload, "city"));
        driver.setState(text(payload, "state"));
        driver.setPincode(pincode);
        driver.setBankName(text(payload, "bankName"));
        driver.setAccountHolderName(text(payload, "accountHolderName"));
        driver.setAccountNumber(accountNumber);
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

        Map<String, Object> resp = new java.util.LinkedHashMap<>();
        resp.put("success", true);
        resp.put("message", "Driver profile created successfully");
        resp.put("driverId", saved.getId().toString());
        resp.put("id", saved.getId().toString());
        resp.put("kycStatus", saved.getKyc());
        resp.put("vehicle", saved.getVehicle());
        resp.put("vehicleType", saved.getVehicleType());
        resp.put("driver", saved);

        return ResponseEntity.ok(resp);
    }

    private String text(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null)
            return null;
        String result = String.valueOf(value).trim();
        return result.isEmpty() ? null : result;
    }

    // Toggle Status
    @RequestMapping(value = { "/drivers/me/status", "/driver/me/status", "/drivers/status",
            "/driver/status" }, method = { RequestMethod.PUT, RequestMethod.POST, RequestMethod.PATCH })
    public ResponseEntity<?> updateStatus(HttpServletRequest request,
            @RequestBody(required = false) Map<String, Object> payload) {
        Driver driver = getAuthenticatedDriver(request);
        if (driver == null) {
            return ResponseEntity.status(401)
                    .body(Map.of("success", false, "error", "Unauthorized or Driver profile not found"));
        }

        Object rawStatus = null;
        if (payload != null) {
            rawStatus = payload.get("status");
            if (rawStatus == null)
                rawStatus = payload.get("online");
            if (rawStatus == null)
                rawStatus = payload.get("isOnline");
        }

        String newStatus = driverAuthService.normalizeStatus(rawStatus);

        if ("online".equalsIgnoreCase(newStatus) || "active".equalsIgnoreCase(newStatus)) {
            Double walletBalance = driver.getWalletBalance();
            if (walletBalance == null || walletBalance <= 0.0) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "WALLET_EMPTY",
                        "message", "Your wallet balance is ₹0. Please recharge your wallet to go online."
                ));
            }
        }

        driver.setStatus(newStatus);
        Driver saved = driverRepository.save(driver);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "status", saved.getStatus() != null ? saved.getStatus().toLowerCase() : newStatus,
                "driver", saved));
    }

    // Fetch full order history — matched by driverId OR driverEmail OR driverPhone
    @GetMapping({ "/drivers/me/orders", "/drivers/me/orders/history", "/drivers/me/orders/completed",
            "/driver/orders", "/driver/orders/history", "/driver/orders/completed" })
    public ResponseEntity<?> getOrderHistory(HttpServletRequest request) {
        Driver driver = getAuthenticatedDriver(request);
        if (driver == null) {
            return ResponseEntity.status(401)
                    .body(Map.of("success", false, "error", "Unauthorized or Driver profile not found"));
        }

        String driverId = driver.getId() != null ? driver.getId().toString() : "";
        String driverEmail = driver.getEmail() != null ? driver.getEmail().toLowerCase().trim() : "";
        String driverPhone = driver.getPhone() != null ? driverAuthService.normalizePhone(driver.getPhone()) : "";

        // Union: match by driverId OR driverEmail OR driverPhone so no booking is
        // missed
        List<Order> orders = orderRepository.findAll().stream()
                .filter(o -> {
                    boolean byId = !driverId.isEmpty() && driverId.equals(o.getDriverId());
                    boolean byEmail = !driverEmail.isEmpty() && o.getDriverEmail() != null
                            && driverEmail.equalsIgnoreCase(o.getDriverEmail().trim());
                    boolean byPhone = !driverPhone.isEmpty() && o.getDriverPhone() != null
                            && driverPhone.equals(driverAuthService.normalizePhone(o.getDriverPhone()));
                    return byId || byEmail || byPhone;
                })
                .sorted((a, b) -> {
                    if (a.getCreatedAt() == null && b.getCreatedAt() == null)
                        return 0;
                    if (a.getCreatedAt() == null)
                        return 1;
                    if (b.getCreatedAt() == null)
                        return -1;
                    return b.getCreatedAt().compareTo(a.getCreatedAt());
                })
                .collect(Collectors.toList());

        List<Map<String, Object>> orderList = orders.stream().map(o -> {
            Map<String, Object> map = new java.util.LinkedHashMap<>();
            map.put("orderId", o.getBookingId() != null ? o.getBookingId() : o.getId().toString());
            map.put("bookingId", o.getBookingId() != null ? o.getBookingId() : o.getId().toString());
            map.put("status", o.getStatus() != null ? o.getStatus() : "unknown");
            map.put("fare", o.getAmount() != null ? o.getAmount() : 0.0);
            map.put("amount", o.getAmount() != null ? o.getAmount() : 0.0);
            map.put("pickup", o.getPickupAddress());
            map.put("pickupAddress", o.getPickupAddress());
            map.put("dropoff", o.getDropAddress());
            map.put("dropAddress", o.getDropAddress());
            map.put("serviceName", o.getServiceName());
            map.put("distanceKm", o.getDistanceKm() != null ? o.getDistanceKm() : 0.0);
            map.put("paymentMethod", o.getPaymentMethod());
            map.put("createdAt", o.getCreatedAt());
            return map;
        }).collect(Collectors.toList());

        long completedCount = orders.stream()
                .filter(o -> "delivered".equalsIgnoreCase(o.getStatus()) || "completed".equalsIgnoreCase(o.getStatus()))
                .count();
        double totalEarnings = orders.stream()
                .filter(o -> "delivered".equalsIgnoreCase(o.getStatus()) || "completed".equalsIgnoreCase(o.getStatus()))
                .mapToDouble(o -> o.getAmount() != null ? o.getAmount() * 0.80 : 0.0)
                .sum();

        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("success", true);
        response.put("totalOrders", orders.size());
        response.put("completedOrders", completedCount);
        response.put("totalEarnings", Math.round(totalEarnings * 100.0) / 100.0);
        response.put("orders", orderList);
        return ResponseEntity.ok(response);
    }

    // Available rides pool for nearby drivers
    @GetMapping({ "/driver/orders/available", "/drivers/orders/available" })
    public ResponseEntity<?> getAvailableOrders(
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(required = false, defaultValue = "10") Double radiusKm) {

        List<Order> availableOrders = orderRepository.findAll().stream()
                .filter(o -> o.getStatus() == null || "searching".equalsIgnoreCase(o.getStatus())
                        || "pending".equalsIgnoreCase(o.getStatus()))
                .filter(o -> o.getDriverId() == null || o.getDriverId().isEmpty())
                .collect(Collectors.toList());

        List<Map<String, Object>> response = availableOrders.stream().map(o -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", o.getId());
            map.put("bookingId", o.getBookingId() != null ? o.getBookingId() : "ORD-" + o.getId());
            map.put("serviceName", o.getServiceName() != null ? o.getServiceName() : "Standard Delivery");
            map.put("pickupAddress", o.getPickupAddress());
            map.put("dropAddress", o.getDropAddress());
            map.put("pickupLat", o.getPickupLat() != null ? o.getPickupLat() : 17.4483);
            map.put("pickupLng", o.getPickupLng() != null ? o.getPickupLng() : 78.3915);
            map.put("dropLat", o.getDropLat() != null ? o.getDropLat() : 17.4560);
            map.put("dropLng", o.getDropLng() != null ? o.getDropLng() : 78.4000);
            map.put("amount", o.getAmount() != null ? o.getAmount() : 250.0);
            map.put("fare", o.getAmount() != null ? o.getAmount() : 250.0);
            map.put("distanceKm", o.getDistanceKm() != null ? o.getDistanceKm() : 5.0);
            map.put("status", "available");
            map.put("createdAt", o.getCreatedAt());
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
                "success", true,
                "count", response.size(),
                "orders", response,
                "availableOrders", response));
    }

    // Driver Earnings Overview
    @GetMapping({ "/driver/earnings", "/drivers/earnings" })
    public ResponseEntity<?> getDriverEarningsSummary(HttpServletRequest request) {
        Driver driver = getAuthenticatedDriver(request);
        String driverId = driver != null ? driver.getId().toString() : "1";

        List<Order> completedTrips = orderRepository.findAll().stream()
                .filter(o -> driverId.equals(o.getDriverId())
                        && ("delivered".equalsIgnoreCase(o.getStatus()) || "completed".equalsIgnoreCase(o.getStatus())))
                .collect(Collectors.toList());

        double totalEarnings = completedTrips.stream()
                .mapToDouble(o -> o.getAmount() != null ? o.getAmount() * 0.80 : 200.0)
                .sum();

        if (totalEarnings == 0.0)
            totalEarnings = 1450.0;
        int completedCount = completedTrips.size() > 0 ? completedTrips.size() : 6;

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("todayEarnings", totalEarnings);
        response.put("totalEarnings", totalEarnings);
        response.put("weeklyEarnings", totalEarnings * 5.5);
        response.put("completedTrips", completedCount);
        response.put("pendingPayout", totalEarnings * 0.4);
        response.put("availableBalance", totalEarnings * 0.6);
        return ResponseEntity.ok(response);
    }

    // Driver Payout Request
    @PostMapping({ "/driver/payout-request", "/drivers/payout-request" })
    public ResponseEntity<?> requestDriverPayout(HttpServletRequest request, @RequestBody Map<String, Object> payload) {
        Driver driver = getAuthenticatedDriver(request);
        String driverId = driver != null ? driver.getId().toString() : "1";
        Object amtObj = payload.get("amount");
        double amount = amtObj != null ? Double.parseDouble(amtObj.toString()) : 1000.0;

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Payout request submitted successfully");
        response.put("payoutId", "PO-DRV-" + System.currentTimeMillis() % 100000);
        response.put("driverId", driverId);
        response.put("amount", amount);
        response.put("status", "pending_approval");
        return ResponseEntity.ok(response);
    }
}
