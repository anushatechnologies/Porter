package com.anushaporter.backend.controller;

import com.anushaporter.backend.dto.PassengerBookingCreateRequest;
import com.anushaporter.backend.dto.PassengerFareEstimateRequest;
import com.anushaporter.backend.dto.PassengerFareEstimateResponse;
import com.anushaporter.backend.model.PassengerBooking;
import com.anushaporter.backend.model.PassengerServiceEntity;
import com.anushaporter.backend.model.PassengerVehicleCategory;
import com.anushaporter.backend.model.RentalPackage;
import com.anushaporter.backend.repository.AppUserRepository;
import com.anushaporter.backend.repository.PassengerBookingRepository;
import com.anushaporter.backend.repository.PassengerServiceRepository;
import com.anushaporter.backend.repository.PassengerVehicleCategoryRepository;
import com.anushaporter.backend.repository.RentalPackageRepository;
import com.anushaporter.backend.service.PassengerBookingService;
import com.anushaporter.backend.service.PassengerPricingEngine;
import com.anushaporter.backend.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping({"/api/passenger", "/api/passenger-bookings"})
@RequiredArgsConstructor
@Slf4j
public class PassengerBookingController {

    private final PassengerPricingEngine pricingEngine;
    private final PassengerBookingService bookingService;
    private final PassengerBookingRepository bookingRepository;
    private final PassengerServiceRepository serviceRepository;
    private final PassengerVehicleCategoryRepository vehicleCategoryRepository;
    private final RentalPackageRepository rentalPackageRepository;
    private final JwtUtil jwtUtil;
    private final AppUserRepository appUserRepository;

    @PostMapping({"/fare-estimate", "/fares/estimate"})
    public ResponseEntity<PassengerFareEstimateResponse> getFareEstimate(@RequestBody PassengerFareEstimateRequest request) {
        PassengerFareEstimateResponse response = pricingEngine.calculateFare(request);
        response.setSuccess(true);

        List<PassengerVehicleCategory> categories = vehicleCategoryRepository.findByActiveTrueOrderByDisplayOrderAsc();
        List<Map<String, Object>> estimatesList = new ArrayList<>();
        for (PassengerVehicleCategory cat : categories) {
            Map<String, Object> estMap = new LinkedHashMap<>();
            estMap.put("vehicleCategoryCode", cat.getCategoryCode());
            estMap.put("code", cat.getCategoryCode());
            estMap.put("name", cat.getDisplayName());
            estMap.put("displayName", cat.getDisplayName());
            estMap.put("imageUrl", cat.getImageUrl() != null ? cat.getImageUrl() : "");
            estMap.put("passengerCapacity", cat.getPassengerCapacity());
            estMap.put("luggageCapacity", cat.getLuggageCapacity());
            estMap.put("eta", "3 mins");
            if (cat.getCategoryCode().equalsIgnoreCase(response.getVehicleCategoryCode())) {
                estMap.put("estimatedFare", response.getEstimatedFare());
            } else {
                try {
                    PassengerFareEstimateRequest clone = PassengerFareEstimateRequest.builder()
                            .serviceType(request.getServiceType())
                            .vehicleCategoryCode(cat.getCategoryCode())
                            .pickupAddress(request.getPickupAddress())
                            .pickupLat(request.getPickupLat())
                            .pickupLng(request.getPickupLng())
                            .dropAddress(request.getDropAddress())
                            .dropLat(request.getDropLat())
                            .dropLng(request.getDropLng())
                            .passengerCount(request.getPassengerCount())
                            .luggageCount(request.getLuggageCount())
                            .couponCode(request.getCouponCode())
                            .manualDistanceKm(request.getManualDistanceKm())
                            .manualDurationMinutes(request.getManualDurationMinutes())
                            .build();
                    PassengerFareEstimateResponse cloneResp = pricingEngine.calculateFare(clone);
                    estMap.put("estimatedFare", cloneResp.getEstimatedFare());
                } catch (Exception e) {
                    estMap.put("estimatedFare", cat.getBaseFare() != null ? cat.getBaseFare() : new java.math.BigDecimal("50.00"));
                }
            }
            estimatesList.add(estMap);
        }
        response.setEstimates(estimatesList);
        return ResponseEntity.ok(response);
    }

    @PostMapping({"/bookings", "/bookings/book", "/book"})
    public ResponseEntity<Map<String, Object>> createBooking(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody PassengerBookingCreateRequest request) {

        // Enrich customer details from JWT Bearer token if not provided in payload
        resolveCustomerDetails(authHeader, request);

        PassengerBooking booking = bookingService.createBooking(request);
        Map<String, Object> response = formatBookingResponse(booking);
        response.put("success", true);
        return ResponseEntity.status(201).body(response);
    }

    @GetMapping({"/bookings", "/bookings/list"})
    public ResponseEntity<Map<String, Object>> getCustomerBookings(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize
    ) {
        String resolvedPhone = phone;
        String resolvedEmail = null;
        Long resolvedCustomerId = null;

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            String identifier = jwtUtil.extractIdentifierFromFirebaseOrJwt(token);
            if (identifier != null && !identifier.isBlank()) {
                if (identifier.contains("@")) {
                    resolvedEmail = identifier;
                    var userOpt = appUserRepository.findFirstByEmailOrderByIdDesc(identifier);
                    if (userOpt.isPresent()) {
                        resolvedCustomerId = userOpt.get().getId();
                        if (resolvedPhone == null) resolvedPhone = userOpt.get().getPhone();
                    }
                } else {
                    String clean = identifier.replaceAll("\\D+", "");
                    if (clean.length() > 10) clean = clean.substring(clean.length() - 10);
                    if (resolvedPhone == null && !clean.isEmpty()) resolvedPhone = clean;
                    var userOpt = appUserRepository.findFirstByPhoneOrderByIdDesc(clean);
                    if (userOpt.isPresent()) {
                        resolvedCustomerId = userOpt.get().getId();
                        if (resolvedEmail == null) resolvedEmail = userOpt.get().getEmail();
                    }
                }
            }
        }

        List<PassengerBooking> allBookings;
        if (resolvedPhone != null || resolvedEmail != null || resolvedCustomerId != null) {
            allBookings = bookingRepository.findForCustomer(resolvedPhone, resolvedEmail, resolvedCustomerId);
        } else {
            allBookings = bookingRepository.findAllByOrderByCreatedAtDesc();
        }

        // Status filtering if requested
        if (status != null && !status.isBlank()) {
            String filterStatus = status.trim().toLowerCase();
            allBookings = allBookings.stream().filter(b -> {
                String bStatus = b.getStatus() != null ? b.getStatus().name().toLowerCase() : "";
                if ("active".equals(filterStatus)) {
                    return !bStatus.contains("completed") && !bStatus.contains("cancelled");
                }
                return bStatus.contains(filterStatus);
            }).toList();
        }

        int total = allBookings.size();
        int safePage = Math.max(1, page != null ? page : 1);
        int safePageSize = Math.max(1, pageSize != null ? pageSize : 20);
        int start = (safePage - 1) * safePageSize;
        List<PassengerBooking> pagedList = start >= total ? Collections.emptyList()
                : allBookings.subList(start, Math.min(start + safePageSize, total));

        List<Map<String, Object>> formattedItems = pagedList.stream().map(this::formatBookingResponse).toList();

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("success", true);
        resp.put("items", formattedItems);
        resp.put("bookings", formattedItems);
        resp.put("total", total);
        resp.put("page", safePage);
        resp.put("pageSize", safePageSize);
        resp.put("hasMore", (start + safePageSize) < total);

        return ResponseEntity.ok(resp);
    }

    @GetMapping({"/bookings/{id}", "/bookings/{id}/tracking", "/bookings/{id}/live"})
    public ResponseEntity<Map<String, Object>> getBookingById(@PathVariable String id) {
        Optional<PassengerBooking> bookingOpt = findBookingByIdOrNumber(id);
        if (bookingOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Map<String, Object> response = formatBookingResponse(bookingOpt.get());
        response.put("success", true);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/bookings/by-number/{bookingNumber}")
    public ResponseEntity<Map<String, Object>> getBookingByNumber(@PathVariable String bookingNumber) {
        Optional<PassengerBooking> bookingOpt = bookingRepository.findByBookingNumber(bookingNumber);
        if (bookingOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of(
                "success", true,
                "booking", bookingOpt.get()
        ));
    }

    @GetMapping("/bookings/customer/{phone}")
    public ResponseEntity<List<PassengerBooking>> getCustomerBookings(@PathVariable String phone) {
        return ResponseEntity.ok(bookingRepository.findByCustomerPhoneOrderByCreatedAtDesc(phone));
    }

    @PostMapping("/bookings/{id}/cancel")
    public ResponseEntity<Map<String, Object>> cancelBooking(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, String> payload
    ) {
        Optional<PassengerBooking> bookingOpt = findBookingByIdOrNumber(id);
        if (bookingOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        String cancelledBy = (payload != null && payload.containsKey("cancelledBy")) ? payload.get("cancelledBy") : "CUSTOMER";
        String reason = (payload != null && payload.containsKey("reason")) ? payload.get("reason") : "Ride cancelled by user";
        PassengerBooking cancelled = bookingService.cancelBooking(bookingOpt.get().getId(), cancelledBy, reason);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "Ride cancelled successfully");
        response.put("cancellationFee", cancelled.getCancellationFee() != null ? cancelled.getCancellationFee() : 0);
        response.put("booking", cancelled);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/bookings/{id}/payment")
    public ResponseEntity<PassengerBooking> processPayment(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, String> payload
    ) {
        Optional<PassengerBooking> bookingOpt = findBookingByIdOrNumber(id);
        if (bookingOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        String paymentMethod = (payload != null && payload.containsKey("paymentMethod")) ? payload.get("paymentMethod") : "ONLINE";
        String txRef = (payload != null && payload.containsKey("transactionRef")) ? payload.get("transactionRef") : "TX-" + System.currentTimeMillis();
        PassengerBooking paid = bookingService.completePayment(bookingOpt.get().getId(), paymentMethod, txRef);
        return ResponseEntity.ok(paid);
    }

    @PostMapping({"/bookings/{id}/review", "/bookings/{id}/rate"})
    public ResponseEntity<Map<String, Object>> rateTrip(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> payload
    ) {
        Optional<PassengerBooking> bookingOpt = findBookingByIdOrNumber(id);
        if (bookingOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Integer rating = (payload != null && payload.containsKey("rating")) ? Integer.valueOf(payload.get("rating").toString()) : 5;
        String notes = (payload != null && payload.containsKey("feedback")) ? payload.get("feedback").toString()
                : (payload != null && payload.containsKey("notes") ? payload.get("notes").toString() : "");
        PassengerBooking rated = bookingService.rateTrip(bookingOpt.get().getId(), rating, notes);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "Review submitted successfully");
        response.put("booking", rated);
        return ResponseEntity.ok(response);
    }

    @GetMapping({"/categories", "/vehicles"})
    public ResponseEntity<Map<String, Object>> getVehicleCategories() {
        List<PassengerVehicleCategory> categories = vehicleCategoryRepository.findByActiveTrueOrderByDisplayOrderAsc();
        List<Map<String, Object>> data = categories.stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getCategoryCode() != null ? c.getCategoryCode().toLowerCase() : String.valueOf(c.getId()));
            m.put("code", c.getCategoryCode());
            m.put("categoryCode", c.getCategoryCode());
            m.put("name", c.getDisplayName());
            m.put("displayName", c.getDisplayName());
            m.put("description", c.getDescription());
            m.put("imageUrl", c.getImageUrl() != null ? c.getImageUrl() : "");
            m.put("passengerCapacity", c.getPassengerCapacity());
            m.put("luggageCapacity", c.getLuggageCapacity());
            m.put("basePrice", c.getBaseFare());
            m.put("baseFare", c.getBaseFare());
            m.put("perKmRate", c.getPerKmRate());
            m.put("perHourRate", c.getPerHourRate());
            m.put("minimumFare", c.getMinimumFare());
            m.put("minimumKm", c.getMinimumKm());
            m.put("driverAllowance", c.getDriverAllowance());
            m.put("eta", "3 mins");
            m.put("isActive", Boolean.TRUE.equals(c.getActive()));
            m.put("active", Boolean.TRUE.equals(c.getActive()));
            m.put("displayOrder", c.getDisplayOrder());
            return m;
        }).toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("vehicles", data);
        response.put("data", data);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/services")
    public ResponseEntity<Map<String, Object>> getServices() {
        List<PassengerServiceEntity> entities = serviceRepository.findByActiveTrueOrderByDisplayOrderAsc();
        List<Map<String, Object>> services = entities.stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getServiceCode() != null ? s.getServiceCode().toLowerCase().replace("_", "-") : String.valueOf(s.getId()));
            m.put("code", s.getServiceCode());
            m.put("serviceCode", s.getServiceCode());
            m.put("name", s.getDisplayName());
            m.put("displayName", s.getDisplayName());
            m.put("description", s.getDescription());
            m.put("iconUrl", s.getIconUrl());
            m.put("isActive", Boolean.TRUE.equals(s.getActive()));
            m.put("active", Boolean.TRUE.equals(s.getActive()));
            m.put("displayOrder", s.getDisplayOrder());
            return m;
        }).toList();

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("success", true);
        resp.put("services", services);
        resp.put("data", services);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/vehicles/available")
    public ResponseEntity<List<PassengerVehicleCategory>> getAvailableVehicles(
            @RequestParam(required = false) Integer passengers
    ) {
        if (passengers != null && passengers > 0) {
            return ResponseEntity.ok(vehicleCategoryRepository
                    .findByActiveTrueAndPassengerCapacityGreaterThanEqualOrderByDisplayOrderAsc(passengers));
        }
        return ResponseEntity.ok(vehicleCategoryRepository.findByActiveTrueOrderByDisplayOrderAsc());
    }

    @GetMapping("/rental-packages")
    public ResponseEntity<List<RentalPackage>> getRentalPackages(
            @RequestParam(required = false) String vehicleCategoryCode
    ) {
        if (vehicleCategoryCode != null && !vehicleCategoryCode.isBlank()) {
            return ResponseEntity.ok(rentalPackageRepository
                    .findByVehicleCategoryCodeAndActiveTrue(PassengerPricingEngine.normalizeCategoryCode(vehicleCategoryCode)));
        }
        return ResponseEntity.ok(rentalPackageRepository.findByActiveTrue());
    }

    private Optional<PassengerBooking> findBookingByIdOrNumber(String id) {
        try {
            Long num = Long.parseLong(id);
            Optional<PassengerBooking> byId = bookingRepository.findById(num);
            if (byId.isPresent()) return byId;
        } catch (NumberFormatException ignored) {}

        Optional<PassengerBooking> byNum = bookingRepository.findByBookingNumber(id);
        if (byNum.isPresent()) return byNum;

        // Strip prefixes like TRK-PASS-
        String sanitized = id.replace("TRK-PASS-", "").replace("TRK-", "").replace("PB-", "");
        try {
            Long num = Long.parseLong(sanitized);
            Optional<PassengerBooking> bySanitized = bookingRepository.findById(num);
            if (bySanitized.isPresent()) return bySanitized;
        } catch (NumberFormatException ignored) {}

        return bookingRepository.findByBookingNumber("AP-CAR-" + sanitized);
    }

    private Map<String, Object> formatBookingResponse(PassengerBooking b) {
        Map<String, Object> response = new LinkedHashMap<>();
        String bookingNum = b.getBookingNumber() != null ? b.getBookingNumber() : String.valueOf(b.getId());
        response.put("id", bookingNum);
        response.put("bookingId", bookingNum);
        response.put("bookingNumber", bookingNum);
        response.put("trackingNumber", b.getTrackingNumber() != null ? b.getTrackingNumber() : bookingNum);
        response.put("serviceCategory", "passenger");
        response.put("serviceType", b.getServiceType() != null ? b.getServiceType() : "PASSENGER");
        response.put("serviceName", b.getVehicleCategoryCode() != null ? b.getVehicleCategoryCode() : "Passenger Ride");
        response.put("vehicleCategory", b.getVehicleCategoryCode());
        response.put("vehicleCategoryCode", b.getVehicleCategoryCode());
        response.put("status", b.getStatus() != null ? b.getStatus().name() : "DRIVER_SEARCHING");
        response.put("startOtp", b.getStartOtp());
        response.put("deliveryOtp", b.getStartOtp());

        BigDecimal fare = (b.getFareBreakdown() != null && b.getFareBreakdown().getTotalFare() != null)
                ? b.getFareBreakdown().getTotalFare() : BigDecimal.ZERO;
        response.put("amount", fare);
        response.put("estimatedFare", fare);
        response.put("pickupAddress", b.getPickupAddress() != null ? b.getPickupAddress() : "");
        response.put("dropAddress", b.getDropAddress() != null ? b.getDropAddress() : "");
        response.put("pickupLat", b.getPickupLatitude());
        response.put("pickupLng", b.getPickupLongitude());
        response.put("dropLat", b.getDropLatitude());
        response.put("dropLng", b.getDropLongitude());
        response.put("paymentMethod", b.getPaymentMethod() != null ? b.getPaymentMethod() : "CASH");
        response.put("paymentMode", b.getPaymentMethod() != null ? b.getPaymentMethod() : "CASH");
        response.put("paymentStatus", b.getPaymentStatus() != null ? b.getPaymentStatus() : "PENDING");
        response.put("createdAt", b.getCreatedAt());
        Map<String, Object> driverMap = null;
        if (b.getDriverId() != null || (b.getDriverName() != null && !b.getDriverName().isBlank())) {
            driverMap = new LinkedHashMap<>();
            driverMap.put("id", b.getDriverId());
            driverMap.put("driverId", b.getDriverId());
            driverMap.put("name", b.getDriverName());
            driverMap.put("phone", b.getDriverPhone());
            driverMap.put("vehicleNumber", b.getVehicleNumber());
            driverMap.put("vehicleModel", b.getVehicleModel());
            driverMap.put("vehicleType", b.getVehicleModel() != null ? b.getVehicleModel() : b.getVehicleCategoryCode());
            driverMap.put("serviceType", "PASSENGER");
            driverMap.put("rating", 4.8);
        }
        response.put("driver", driverMap != null ? driverMap : b.getDriver());
        response.put("assignedDriver", driverMap != null ? driverMap : b.getDriver());
        response.put("hasAssignedDriver", driverMap != null || b.getDriver() != null || b.getDriverId() != null);
        response.put("trackable", b.getStatus() != null && !b.getStatus().isTerminal());
        response.put("booking", b);
        return response;
    }

    private void resolveCustomerDetails(String authHeader, PassengerBookingCreateRequest request) {
        if (request == null) return;

        String identifier = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            identifier = jwtUtil.extractIdentifierFromFirebaseOrJwt(token);
        }

        String phone = (request.getCustomerPhone() != null && !request.getCustomerPhone().isBlank() && !"N/A".equalsIgnoreCase(request.getCustomerPhone()))
                ? request.getCustomerPhone() : null;
        String email = (request.getCustomerEmail() != null && !request.getCustomerEmail().isBlank())
                ? request.getCustomerEmail() : null;
        Long customerId = request.getCustomerId();
        String customerName = (request.getCustomerName() != null && !request.getCustomerName().isBlank() && !"Customer".equalsIgnoreCase(request.getCustomerName()))
                ? request.getCustomerName() : null;

        if (identifier != null && !identifier.isBlank()) {
            if (identifier.contains("@")) {
                if (email == null) email = identifier;
                var userOpt = appUserRepository.findFirstByEmailOrderByIdDesc(identifier);
                if (userOpt.isPresent()) {
                    var u = userOpt.get();
                    if (customerId == null) customerId = u.getId();
                    if (customerName == null && u.getName() != null) customerName = u.getName();
                    if (phone == null && u.getPhone() != null) phone = u.getPhone();
                }
            } else {
                String cleanDigits = identifier.replaceAll("\\D+", "");
                if (cleanDigits.length() > 10) cleanDigits = cleanDigits.substring(cleanDigits.length() - 10);
                if (phone == null && !cleanDigits.isEmpty()) phone = cleanDigits;

                var userOpt = appUserRepository.findFirstByPhoneOrderByIdDesc(cleanDigits);
                if (userOpt.isPresent()) {
                    var u = userOpt.get();
                    if (customerId == null) customerId = u.getId();
                    if (customerName == null && u.getName() != null) customerName = u.getName();
                    if (email == null && u.getEmail() != null) email = u.getEmail();
                }
            }
        }

        if (phone != null && !phone.isBlank()) {
            String cleanPhone = phone.replaceAll("\\D+", "");
            if (cleanPhone.length() > 10) cleanPhone = cleanPhone.substring(cleanPhone.length() - 10);
            if (customerId == null) {
                var userOpt = appUserRepository.findFirstByPhoneOrderByIdDesc(cleanPhone);
                if (userOpt.isPresent()) {
                    customerId = userOpt.get().getId();
                    if (customerName == null && userOpt.get().getName() != null) customerName = userOpt.get().getName();
                }
            }
            if (email == null) {
                email = cleanPhone + "@customer.porter.in";
            }
            request.setCustomerPhone(phone);
        }

        if (customerId != null) request.setCustomerId(customerId);
        if (customerName != null) request.setCustomerName(customerName);
        if (email != null) request.setCustomerEmail(email);
    }
}
